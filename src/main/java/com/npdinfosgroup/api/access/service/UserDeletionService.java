package com.npdinfosgroup.api.access.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.dto.request.UserDeletionCommand;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Deletes a user at once (SCRUM-231): from Keycloak, which ends its sessions, and from {@code app_user},
 * keeping its name, email and role in the audit log. See {@code docs/specs/SCRUM-231/design.md} §1 and §2.
 * <p>
 * Unlike creating or editing a user, Keycloak is called inside the transaction with the rows locked: a
 * deleted account cannot be restored, so the version and the last-administrator rule (CA-04) are checked
 * under the lock and stay true until the commit. If Keycloak fails, nothing is deleted; if the commit
 * fails after Keycloak deleted the account, deleting the user again completes it. The employee record of
 * an NPD lead is never touched (CA-09).
 */
@Service
public class UserDeletionService {

	private static final Logger log = LoggerFactory.getLogger(UserDeletionService.class);

	/** Entity name of the users in {@code audit_log}, as their creation records it. */
	private static final String AUDIT_ENTITY = "app_user";

	private static final String ADMINISTRATOR = Role.OPERATIONS_ADMINISTRATOR.realmName();

	private final AppUserRepository users;

	private final KeycloakAdminClient keycloak;

	private final AuditService audit;

	private final TransactionOperations transactions;

	/**
	 * Creates the service.
	 *
	 * @param users reads, locks and deletes the user
	 * @param keycloak deletes the account
	 * @param audit records the deletion
	 * @param transactions runs the checks, the deletion and the audit with the rows locked
	 */
	UserDeletionService(AppUserRepository users, KeycloakAdminClient keycloak, AuditService audit,
			TransactionOperations transactions) {
		this.users = users;
		this.keycloak = keycloak;
		this.audit = audit;
		this.transactions = transactions;
	}

	/**
	 * Deletes a user from Keycloak and from {@code app_user} (CA-01), audited with who deleted it and its
	 * name, email and role (CA-06).
	 *
	 * @param command the user id, the version that was read and who deletes it
	 * @throws NotFoundException if the user does not exist
	 * @throws ValidationException if the version is missing
	 * @throws ApiException {@link ErrorCode#CONCURRENT_MODIFICATION} if someone saved the user after the
	 *         version that was read, or {@link ErrorCode#LAST_OPERATIONS_ADMINISTRATOR} if it is the only
	 *         active Operations administrator (CA-04); nothing is deleted
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached; nothing is deleted
	 */
	public void delete(UserDeletionCommand command) {
		if (command.version() == null) {
			throw new ValidationException(CreateUserRequestValidator.MESSAGE_SCOPE,
					List.of(FieldProblem.of("version", FieldErrorCode.REQUIRED)));
		}
		long id = command.id();
		AppUser read = users.findById(id).orElseThrow(() -> new NotFoundException(id));
		// By role, not state: a PENDING administrator may become ACTIVE before the lock without a new version
		boolean administrator = ADMINISTRATOR.equals(read.getRole());
		transactions.executeWithoutResult(status -> deleteLocked(command, administrator));
		log.info("User {} deleted", id);
	}

	/**
	 * Locks the active administrators (for an administrator) and then the user, in the same order as the
	 * user edit, so two administrators deleting or demoting each other wait for one another and the second
	 * one is rejected; then rechecks and deletes.
	 */
	private void deleteLocked(UserDeletionCommand command, boolean administrator) {
		long id = command.id();
		List<AppUser> administrators = administrator ? users.lockByRoleAndState(ADMINISTRATOR, AppUserState.ACTIVE)
				: List.of();
		AppUser user = users.lockById(id).orElseThrow(() -> new NotFoundException(id));
		if (!Objects.equals(command.version(), user.getVersion())) {
			throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(),
					Map.of("currentVersion", user.getVersion()));
		}
		boolean activeAdministrator = ADMINISTRATOR.equals(user.getRole()) && user.getState() == AppUserState.ACTIVE;
		if (activeAdministrator && administrators.stream().allMatch(other -> other.getId() == id)) {
			throw new ApiException(ErrorCode.LAST_OPERATIONS_ADMINISTRATOR, List.of(), Map.of());
		}
		keycloak.deleteAccount(user.getKeycloakId());
		Map<String, String> lastValues = new LinkedHashMap<>();
		lastValues.put("fullName", user.getFullName());
		lastValues.put("email", user.getEmail());
		lastValues.put("role", user.getRole());
		users.delete(user);
		users.flush();
		audit.recordDeletion(AUDIT_ENTITY, id, lastValues, command.actor());
	}

}
