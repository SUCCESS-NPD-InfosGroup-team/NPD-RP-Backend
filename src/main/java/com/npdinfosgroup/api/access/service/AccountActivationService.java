package com.npdinfosgroup.api.access.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Deactivates a user account (SCRUM-231, first version, being removed): Keycloak first, then the {@code app_user} row and its audit in
 * one transaction. See {@code docs/specs/SCRUM-231/design.md} §1, §2 and §5.
 * <p>
 * The Keycloak account is disabled, so it cannot log in again, and its sessions are closed, so its next
 * request is refused (CA-01). Keycloak and the database cannot share a transaction: no transaction stays
 * open while waiting for Keycloak, and if anything fails after the account was disabled, it is enabled
 * again. The employee record of an NPD lead is never touched (CA-09).
 */
@Service
public class AccountActivationService {

	private static final Logger log = LoggerFactory.getLogger(AccountActivationService.class);

	/** Entity name of the users in {@code audit_log}, as their creation records it. */
	private static final String AUDIT_ENTITY = "app_user";

	private static final String STATE = "state";

	private final AppUserRepository users;

	private final KeycloakAdminClient keycloak;

	private final AuditService audit;

	private final TransactionOperations transactions;

	private final Clock clock;

	private final UserQueryService queries;

	private final AccountDeletionProperties deletion;

	private final ApplicationEventPublisher events;

	/**
	 * Creates the service.
	 *
	 * @param users reads, locks and saves the user
	 * @param keycloak disables and enables the account and closes its sessions
	 * @param audit records the change of state
	 * @param transactions runs the save and the audit in one transaction
	 * @param clock gives the moment of the deactivation
	 * @param queries builds the response
	 * @param deletion when a deactivated account is deleted
	 * @param events schedules or cancels the deletion once the change is committed
	 */
	AccountActivationService(AppUserRepository users, KeycloakAdminClient keycloak, AuditService audit,
			TransactionOperations transactions, Clock clock, UserQueryService queries,
			AccountDeletionProperties deletion, ApplicationEventPublisher events) {
		this.users = users;
		this.keycloak = keycloak;
		this.audit = audit;
		this.transactions = transactions;
		this.clock = clock;
		this.queries = queries;
		this.deletion = deletion;
		this.events = events;
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
	 *         version that was read; nothing changes
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 */
	public UserResponse deactivate(AccountActivationCommand command) {
		AppUser user = checked(command);
		if (user.getState() == AppUserState.DISABLED) {
			return queries.toResponse(user);
		}
		UUID keycloakId = user.getKeycloakId();
		keycloak.setEnabled(keycloakId, false);
		keycloak.logout(keycloakId);
		UserResponse response = transactions.execute(status -> saveDeactivation(command));
		log.info("User {} deactivated", command.id());
		return response;
	}

	/** Saves the deactivation with the user locked. */
	private UserResponse saveDeactivation(AccountActivationCommand command) {
		long id = command.id();
		AppUser user = users.lockById(id).orElseThrow(() -> new NotFoundException(id));
		String before = user.getState().name();
		user.setState(AppUserState.DISABLED);
		user.setDisabledAt(clock.instant());
		users.saveAndFlush(user);
		audit.recordChanges(AUDIT_ENTITY, id, Map.of(STATE, before), Map.of(STATE, AppUserState.DISABLED.name()),
				command.actor());
		events.publishEvent(new AccountStateChanged(id, deletion.deletesAt(user.getDisabledAt())));
		return queries.toResponse(user);
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

	private static ApiException concurrentModification(AppUser current) {
		return new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(),
				Map.of("currentVersion", current.getVersion()));
	}

}
