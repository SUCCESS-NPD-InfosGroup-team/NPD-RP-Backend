package com.npdinfosgroup.api.access.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.dto.request.UpdateUserCommand;
import com.npdinfosgroup.api.access.dto.request.UpdateUserRequest;
import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.access.service.validator.CreateUserRequestValidator;
import com.npdinfosgroup.api.access.service.validator.UpdateUserRequestValidator;
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
 * Edits the full name and the role of a user (SCRUM-53): Keycloak first, then the {@code app_user} row
 * and its audit in one transaction. See {@code docs/specs/SCRUM-53/design.md} §1, §3 and §5.
 * <p>
 * Keycloak and the database cannot share a transaction, so no transaction stays open while waiting for
 * Keycloak. A role change closes the user's sessions, so the new role applies from their next request
 * (CA-01).
 * <p>
 * <b>For now</b> the role cannot change to NPD lead: that needs the employee record of the lead, which
 * comes with the NPD lead cases (SCRUM-53 plan, PR 8).
 */
@Service
public class UserEditService {

	private static final Logger log = LoggerFactory.getLogger(UserEditService.class);

	/** Entity name of the users in {@code audit_log}, as their creation records it. */
	private static final String AUDIT_ENTITY = "app_user";

	private final UpdateUserRequestValidator validator;

	private final AppUserRepository users;

	private final KeycloakAdminClient keycloak;

	private final AuditService audit;

	private final TransactionOperations transactions;

	private final UserQueryService queries;

	/**
	 * Creates the service.
	 *
	 * @param validator checks the request
	 * @param users reads, locks and saves the user
	 * @param keycloak renames the account, replaces its role and closes its sessions
	 * @param audit records the changed fields
	 * @param transactions runs the save and the audit in one transaction
	 * @param queries builds the response
	 */
	UserEditService(UpdateUserRequestValidator validator, AppUserRepository users, KeycloakAdminClient keycloak,
			AuditService audit, TransactionOperations transactions, UserQueryService queries) {
		this.validator = validator;
		this.users = users;
		this.keycloak = keycloak;
		this.audit = audit;
		this.transactions = transactions;
		this.queries = queries;
	}

	/**
	 * Changes the full name and the role of a user. Saving the values it already has changes nothing: no
	 * call to Keycloak, no audit, same version (CA-05).
	 *
	 * @param command the user id, its new values with the version that was read, and who edits it
	 * @return the user as it is saved
	 * @throws NotFoundException if the user does not exist
	 * @throws ValidationException with every invalid field; nothing changes
	 * @throws ApiException {@link ErrorCode#CONCURRENT_MODIFICATION} if someone saved the user after the
	 *         version that was read, or {@link ErrorCode#NPD_LEAD_WITHOUT_EMPLOYEE} for an NPD lead
	 *         without employee record (CA-13)
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 */
	public UserResponse update(UpdateUserCommand command) {
		long id = command.id();
		UpdateUserRequest request = command.user();
		AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException(id));
		// A stale form is reported before its fields: they may be wrong only because the user changed
		if (request.version() != null && !request.version().equals(user.getVersion())) {
			throw concurrentModification(user);
		}
		Role role = validator.validate(request);
		String fullName = request.fullName().strip();
		if (Role.NPD_LEADER.realmName().equals(user.getRole()) && user.getEmployeeId() == null) {
			throw new ApiException(ErrorCode.NPD_LEAD_WITHOUT_EMPLOYEE, List.of(user.getEmail()), Map.of());
		}
		boolean renames = !fullName.equals(user.getFullName());
		boolean changesRole = !role.realmName().equals(user.getRole());
		if (!renames && !changesRole) {
			return queries.toResponse(user);
		}
		if (changesRole && role == Role.NPD_LEADER) {
			throw new ValidationException(CreateUserRequestValidator.MESSAGE_SCOPE,
					List.of(FieldProblem.of("role", FieldErrorCode.ROLE_NOT_ASSIGNABLE)));
		}
		UUID keycloakId = user.getKeycloakId();
		if (renames) {
			keycloak.rename(keycloakId, fullName);
		}
		if (changesRole) {
			keycloak.replaceRealmRole(keycloakId, role);
			keycloak.logout(keycloakId);
		}
		UserResponse response = transactions
			.execute(status -> save(id, request.version(), fullName, role, command.actor()));
		log.info("User {} updated", id);
		return response;
	}

	/**
	 * Saves the new values with the user locked, rechecking the version: someone may have saved the user
	 * while Keycloak was called.
	 */
	private UserResponse save(long id, Integer version, String fullName, Role role, String actor) {
		AppUser user = users.lockById(id).orElseThrow(() -> new NotFoundException(id));
		if (!Objects.equals(version, user.getVersion())) {
			throw concurrentModification(user);
		}
		Map<String, String> before = auditValues(user);
		user.setFullName(fullName);
		user.setRole(role.realmName());
		try {
			users.saveAndFlush(user);
		}
		catch (OptimisticLockingFailureException ex) {
			throw concurrentModification(user);
		}
		audit.recordChanges(AUDIT_ENTITY, id, before, auditValues(user), actor);
		return queries.toResponse(user);
	}

	private static Map<String, String> auditValues(AppUser user) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("fullName", user.getFullName());
		values.put("role", user.getRole());
		return values;
	}

	private static ApiException concurrentModification(AppUser current) {
		return new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(),
				Map.of("currentVersion", current.getVersion()));
	}

}
