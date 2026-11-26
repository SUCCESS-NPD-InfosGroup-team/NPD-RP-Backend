package com.npdinfosgroup.api.access.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.dto.request.AccountActivationCommand;
import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.access.service.validator.CreateUserRequestValidator;
import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import com.npdinfosgroup.api.shared.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Deactivates and reactivates a user account (SCRUM-231): Keycloak first, then the {@code app_user} row and its audit in
 * one transaction. See {@code docs/specs/SCRUM-231/design.md} §1, §2 and §5.
 * <p>
 * The Keycloak account is disabled, so it cannot log in again, and its sessions are closed, so its next
 * request is refused (CA-01). Keycloak and the database cannot share a transaction: no transaction stays
 * open while waiting for Keycloak, and if anything fails after the account was disabled, it is enabled
 * again (disabled again, after a failed reactivation). The employee record of an NPD lead is never touched (CA-09).
 */
@Service
public class AccountActivationService {

	private static final Logger log = LoggerFactory.getLogger(AccountActivationService.class);

	/** Entity name of the users in {@code audit_log}, as their creation records it. */
	private static final String AUDIT_ENTITY = "app_user";

	private static final String STATE = "state";

	private static final String ADMINISTRATOR = Role.OPERATIONS_ADMINISTRATOR.realmName();

	private final AppUserRepository users;

	private final KeycloakAdminClient keycloak;

	private final AuditService audit;

	private final TransactionOperations transactions;

	private final Clock clock;

	private final UserQueryService queries;

	private final AccountDeletionProperties deletion;

	/**
	 * Creates the service.
	 *
	 * @param users reads, locks and saves the user
	 * @param keycloak disables and enables the account and closes its sessions
	 * @param audit records the change of state
	 * @param transactions runs the save and the audit in one transaction
	 * @param clock gives the moment of the deactivation
	 * @param queries builds the response
	 * @param deletion when a deactivated account is deleted, after which it cannot be reactivated
	 */
	AccountActivationService(AppUserRepository users, KeycloakAdminClient keycloak, AuditService audit,
			TransactionOperations transactions, Clock clock, UserQueryService queries,
			AccountDeletionProperties deletion) {
		this.users = users;
		this.keycloak = keycloak;
		this.audit = audit;
		this.transactions = transactions;
		this.clock = clock;
		this.queries = queries;
		this.deletion = deletion;
	}

	/**
	 * Deactivates a user account: disabled in Keycloak with its sessions closed, and {@code DISABLED} with
	 * the moment in {@code app_user} (CA-01), audited (CA-06). An account already deactivated is returned
	 * as it is, with no call to Keycloak.
	 *
	 * @param command the user id, the version that was read and who deactivates it
	 * @return the user as it is saved
	 * @throws NotFoundException if the user does not exist
	 * @throws ValidationException if the version is missing
	 * @throws ApiException {@link ErrorCode#CONCURRENT_MODIFICATION} if someone saved the user after the
	 *         version that was read, or {@link ErrorCode#LAST_OPERATIONS_ADMINISTRATOR} if it is the only
	 *         active Operations administrator (CA-04); nothing changes
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached; nothing is left changed
	 */
	public UserResponse deactivate(AccountActivationCommand command) {
		AppUser user = checked(command);
		if (user.getState() == AppUserState.DISABLED) {
			return queries.toResponse(user);
		}
		// By role, not state: a PENDING administrator may become ACTIVE before the transaction (without a
		// new version), and the transaction must then have the administrators locked to recheck CA-04
		boolean administrator = ADMINISTRATOR.equals(user.getRole());
		if (isActiveAdministrator(user)
				&& users.countByRoleAndStateAndIdNot(ADMINISTRATOR, AppUserState.ACTIVE, user.getId()) == 0) {
			throw lastAdministrator();
		}
		UUID keycloakId = user.getKeycloakId();
		try {
			keycloak.setEnabled(keycloakId, false);
			keycloak.logout(keycloakId);
			UserResponse response = transactions.execute(status -> saveDeactivation(command, administrator));
			log.info("User {} deactivated", command.id());
			return response;
		}
		catch (RuntimeException ex) {
			// Enabled again even if disabling itself failed: it may have been applied before the error
			undo(keycloakId, true);
			throw ex;
		}
	}

	/**
	 * Saves the deactivation with the user locked, rechecking what may have changed while Keycloak was
	 * called: the version and, for an administrator, that another active one remains. The administrators
	 * are locked before the user, in the same order as the user edit, so two of them deactivating or
	 * demoting each other at the same time wait for one another and the second one is rejected.
	 */
	private UserResponse saveDeactivation(AccountActivationCommand command, boolean administrator) {
		long id = command.id();
		List<AppUser> administrators = administrator ? users.lockByRoleAndState(ADMINISTRATOR, AppUserState.ACTIVE)
				: List.of();
		AppUser user = users.lockById(id).orElseThrow(() -> new NotFoundException(id));
		if (!Objects.equals(command.version(), user.getVersion())) {
			throw concurrentModification(user);
		}
		if (isActiveAdministrator(user) && administrators.stream().allMatch(other -> other.getId() == id)) {
			throw lastAdministrator();
		}
		String before = user.getState().name();
		user.setState(AppUserState.DISABLED);
		user.setDisabledAt(clock.instant());
		try {
			users.saveAndFlush(user);
		}
		catch (OptimisticLockingFailureException ex) {
			throw concurrentModification(user);
		}
		audit.recordChanges(AUDIT_ENTITY, id, Map.of(STATE, before), Map.of(STATE, AppUserState.DISABLED.name()),
				command.actor());
		return queries.toResponse(user);
	}

	/**
	 * Reactivates a deactivated account before its deletion time (CA-02): enabled in Keycloak with the
	 * role it kept, and back to {@code ACTIVE} in {@code app_user}, or to {@code PENDING} if the user never
	 * accessed the platform; audited (CA-06). An account that is not deactivated is returned as it is.
	 *
	 * @param command the user id, the version that was read and who reactivates it
	 * @return the user as it is saved
	 * @throws NotFoundException if the user does not exist (also once deleted)
	 * @throws ValidationException if the version is missing
	 * @throws ApiException {@link ErrorCode#CONCURRENT_MODIFICATION} if someone saved the user after the
	 *         version that was read, or {@link ErrorCode#ACCOUNT_DELETION_DUE} if its deletion time has
	 *         passed; nothing changes
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached; nothing is left changed
	 */
	public UserResponse reactivate(AccountActivationCommand command) {
		AppUser user = checked(command);
		if (user.getState() != AppUserState.DISABLED) {
			return queries.toResponse(user);
		}
		checkNotDue(user);
		UUID keycloakId = user.getKeycloakId();
		try {
			keycloak.setEnabled(keycloakId, true);
			UserResponse response = transactions.execute(status -> saveReactivation(command));
			log.info("User {} reactivated", command.id());
			return response;
		}
		catch (RuntimeException ex) {
			undo(keycloakId, false);
			throw ex;
		}
	}

	/**
	 * Saves the reactivation with the user locked, rechecking the version and the deletion time, which may
	 * have passed while Keycloak was called. The lock also keeps the deletion of the account from running
	 * at the same time: whichever comes second sees what the first one saved.
	 */
	private UserResponse saveReactivation(AccountActivationCommand command) {
		long id = command.id();
		AppUser user = users.lockById(id).orElseThrow(() -> new NotFoundException(id));
		if (!Objects.equals(command.version(), user.getVersion())) {
			throw concurrentModification(user);
		}
		checkNotDue(user);
		AppUserState after = user.getLastAccessAt() == null ? AppUserState.PENDING : AppUserState.ACTIVE;
		user.setState(after);
		user.setDisabledAt(null);
		try {
			users.saveAndFlush(user);
		}
		catch (OptimisticLockingFailureException ex) {
			throw concurrentModification(user);
		}
		audit.recordChanges(AUDIT_ENTITY, id, Map.of(STATE, AppUserState.DISABLED.name()), Map.of(STATE, after.name()),
				command.actor());
		return queries.toResponse(user);
	}

	/** A deactivated account can be reactivated only before the deletion time it was given. */
	private void checkNotDue(AppUser user) {
		Instant deletesAt = deletion.deletesAt(user.getDisabledAt());
		if (deletesAt != null && !clock.instant().isBefore(deletesAt)) {
			throw new ApiException(ErrorCode.ACCOUNT_DELETION_DUE, List.of(), Map.of("deletesAt", deletesAt));
		}
	}

	/**
	 * The user, once the request is known to be current: the version is required and must be the one
	 * saved.
	 */
	private AppUser checked(AccountActivationCommand command) {
		if (command.version() == null) {
			throw new ValidationException(CreateUserRequestValidator.MESSAGE_SCOPE,
					List.of(FieldProblem.of("version", FieldErrorCode.REQUIRED)));
		}
		AppUser user = users.findById(command.id()).orElseThrow(() -> new NotFoundException(command.id()));
		if (!command.version().equals(user.getVersion())) {
			throw concurrentModification(user);
		}
		return user;
	}

	/**
	 * Puts the account back as it was in Keycloak after a failed deactivation or reactivation, best
	 * effort: a failure is logged with the account id to fix it by hand.
	 */
	private void undo(UUID keycloakId, boolean enabled) {
		try {
			keycloak.setEnabled(keycloakId, enabled);
		}
		catch (RuntimeException ex) {
			log.error("Could not set enabled = {} again on Keycloak account {}: {}", enabled, keycloakId,
					ex.getClass().getName());
		}
	}

	private static boolean isActiveAdministrator(AppUser user) {
		return ADMINISTRATOR.equals(user.getRole()) && user.getState() == AppUserState.ACTIVE;
	}

	private static ApiException concurrentModification(AppUser current) {
		return new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(),
				Map.of("currentVersion", current.getVersion()));
	}

	private static ApiException lastAdministrator() {
		return new ApiException(ErrorCode.LAST_OPERATIONS_ADMINISTRATOR, List.of(), Map.of());
	}

}
