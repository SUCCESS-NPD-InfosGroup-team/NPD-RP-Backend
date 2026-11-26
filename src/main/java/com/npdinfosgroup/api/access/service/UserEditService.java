package com.npdinfosgroup.api.access.service;

import java.util.ArrayList;
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
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.access.service.validator.CreateUserRequestValidator;
import com.npdinfosgroup.api.access.service.validator.UpdateUserRequestValidator;
import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.NewNpdLeadCommand;
import com.npdinfosgroup.api.planner.dto.response.NpdLeadCandidate;
import com.npdinfosgroup.api.planner.service.NpdLeadEmployeeService;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import com.npdinfosgroup.api.shared.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Edits the full name and the role of a user (SCRUM-53): Keycloak first, then the {@code app_user} row
 * and its audit in one transaction. See {@code docs/specs/SCRUM-53/design.md} §1, §3 and §5.
 * <p>
 * Keycloak and the database cannot share a transaction, so no transaction stays open while waiting for
 * Keycloak, and if anything fails after Keycloak was changed, the old name and role are put back there.
 * A role change closes the user's sessions, so the new role applies from their next request (CA-01).
 * <p>
 * An NPD lead user has an employee record in {@code planner}, changed in the same transaction through
 * {@link NpdLeadEmployeeService}: registered or back to Activo when the role changes to NPD lead, on Baja
 * when it changes from it, and renamed with the user. An employee that already
 * exists (same email or number) is linked once the administrator confirms it.
 */
@Service
public class UserEditService {

	private static final Logger log = LoggerFactory.getLogger(UserEditService.class);

	/** Entity name of the users in {@code audit_log}, as their creation records it. */
	private static final String AUDIT_ENTITY = "app_user";

	private static final String ADMINISTRATOR = Role.OPERATIONS_ADMINISTRATOR.realmName();

	private static final String NPD_LEADER = Role.NPD_LEADER.realmName();

	private final UpdateUserRequestValidator validator;

	private final AppUserRepository users;

	private final KeycloakAdminClient keycloak;

	private final AuditService audit;

	private final TransactionOperations transactions;

	private final UserQueryService queries;

	private final NpdLeadEmployeeService npdLeads;

	/**
	 * Creates the service.
	 *
	 * @param validator checks the request
	 * @param users reads, locks and saves the user
	 * @param keycloak renames the account, replaces its role and closes its sessions
	 * @param audit records the changed fields
	 * @param transactions runs the save and the audit in one transaction
	 * @param queries builds the response
	 * @param npdLeads registers and changes the employee record of NPD leads
	 */
	UserEditService(UpdateUserRequestValidator validator, AppUserRepository users, KeycloakAdminClient keycloak,
			AuditService audit, TransactionOperations transactions, UserQueryService queries,
			NpdLeadEmployeeService npdLeads) {
		this.validator = validator;
		this.users = users;
		this.keycloak = keycloak;
		this.audit = audit;
		this.transactions = transactions;
		this.queries = queries;
		this.npdLeads = npdLeads;
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
	 *         version that was read, {@link ErrorCode#NPD_LEAD_WITHOUT_EMPLOYEE} for an NPD lead without
	 *         employee record (CA-13), or {@link ErrorCode#LAST_OPERATIONS_ADMINISTRATOR} if no active
	 *         Operations administrator would remain (CA-04); nothing changes
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached; nothing is left changed
	 */
	public UserResponse update(UpdateUserCommand command) {
		long id = command.id();
		UpdateUserRequest request = command.user();
		AppUser user = users.findById(id).orElseThrow(() -> new NotFoundException(id));
		// A stale form is reported before its fields: they may be wrong only because the user changed
		if (request.version() != null && !request.version().equals(user.getVersion())) {
			throw concurrentModification(user);
		}
		NewLead newLead = validate(request, user, command.actor());
		Role role = Role.ofRealmName(request.role()).orElseThrow();
		String fullName = request.fullName().strip();
		if (NPD_LEADER.equals(user.getRole()) && user.getEmployeeId() == null) {
			throw new ApiException(ErrorCode.NPD_LEAD_WITHOUT_EMPLOYEE, List.of(user.getEmail()), Map.of());
		}
		boolean renames = !fullName.equals(user.getFullName());
		boolean changesRole = !role.realmName().equals(user.getRole());
		if (!renames && !changesRole) {
			return queries.toResponse(user);
		}
		boolean demotesAdministrator = changesRole && ADMINISTRATOR.equals(user.getRole());
		if (demotesAdministrator && user.getState() == AppUserState.ACTIVE
				&& users.countByRoleAndStateAndIdNot(ADMINISTRATOR, AppUserState.ACTIVE, id) == 0) {
			throw lastAdministrator();
		}
		Edit edit = new Edit(user.getKeycloakId(), user.getFullName(), user.getRole());
		try {
			if (renames) {
				keycloak.rename(edit.keycloakId, fullName);
				edit.renamed = true;
			}
			if (changesRole) {
				// Marked before the call: the old role may be gone even if adding the new one fails
				edit.roleTouched = true;
				keycloak.replaceRealmRole(edit.keycloakId, role);
				keycloak.logout(edit.keycloakId);
			}
			UserResponse response = transactions
				.execute(status -> save(new Change(id, request.version(), fullName, role, demotesAdministrator, newLead,
						command.actor())));
			log.info("User {} updated", id);
			return response;
		}
		catch (RuntimeException ex) {
			undo(edit);
			throw ex;
		}
	}

	/**
	 * Every invalid field of the request together: the user's and, when the role changes to NPD lead
	 * for a user without employee record, the employee data it needs (CA-10).
	 *
	 * @return the employee record to register or to link, or {@code null} if none is
	 */
	private NewLead validate(UpdateUserRequest request, AppUser user, String actor) {
		List<FieldProblem> problems = new ArrayList<>();
		try {
			validator.validate(request);
		}
		catch (ValidationException ex) {
			problems.addAll(ex.getProblems());
		}
		NewLead newLead = null;
		boolean registersLead = NPD_LEADER.equals(request.role()) && !NPD_LEADER.equals(user.getRole())
				&& user.getEmployeeId() == null;
		Long linkEmployeeId = registersLead && problems.isEmpty() ? confirmedLink(request, user) : null;
		if (linkEmployeeId != null) {
			return new NewLead(null, linkEmployeeId);
		}
		if (registersLead && request.employee() == null) {
			problems.add(FieldProblem.of("employee", FieldErrorCode.REQUIRED));
		}
		else if (registersLead) {
			String fullName = request.fullName() == null ? null : request.fullName().strip();
			newLead = new NewLead(new NewNpdLeadCommand(request.employee(), fullName, user.getEmail(), actor), null);
			// The record's name is the user's, whose errors are already reported as fullName
			npdLeads.problemsOf(newLead.register())
				.stream()
				.filter(problem -> !"employee.name".equals(problem.field()))
				.forEach(problems::add);
		}
		if (!problems.isEmpty()) {
			throw new ValidationException(CreateUserRequestValidator.MESSAGE_SCOPE, problems);
		}
		return newLead;
	}

	/**
	 * An employee with the user's email or the form's number (HU-10 CA-05 to CA-07, CA-12, CA-13): linked
	 * only if it can be and {@code linkEmployeeId} confirms it; otherwise a 409 to confirm or saying why not.
	 *
	 * @return the id of the employee to link, or {@code null} if there is no such employee
	 */
	private Long confirmedLink(UpdateUserRequest request, AppUser user) {
		String number = request.employee() == null ? null : request.employee().employeeNumber();
		NpdLeadCandidate candidate = npdLeads.findLinkCandidate(user.getEmail(), number).orElse(null);
		if (candidate == null) {
			return null;
		}
		String reason = users.existsByEmployeeId(candidate.id()) ? NpdLeadEmployeeService.HAS_USER
				: !candidate.npdLead() ? NpdLeadEmployeeService.NOT_NPD_LEAD
				: "BAJA".equals(candidate.status()) ? NpdLeadEmployeeService.TERMINATED : null;
		if (reason != null) {
			throw NpdLeadEmployeeService.notLinkable(reason, candidate.matchedBy(), candidate.id(), candidate.name());
		}
		if (candidate.id().equals(request.linkEmployeeId())) {
			return candidate.id();
		}
		throw new ApiException(ErrorCode.EXISTING_NPD_LEAD_EMPLOYEE, List.of(candidate.name(), candidate.employeeNumber()),
				Map.of("matchedBy", candidate.matchedBy(), "existingEmployee", candidate));
	}

	/**
	 * Saves the new values with the user locked, rechecking what may have changed while Keycloak was
	 * called: the version and, when an administrator is demoted, that another active one remains. The
	 * administrators are locked before the user, always in the same order, so two administrators who
	 * demote each other at the same time wait for one another and the second one is rejected. The
	 * employee record changes in the same transaction: both are saved, or neither.
	 */
	private UserResponse save(Change change) {
		long id = change.id();
		List<AppUser> administrators = change.demotesAdministrator()
				? users.lockByRoleAndState(ADMINISTRATOR, AppUserState.ACTIVE) : List.of();
		AppUser user = users.lockById(id).orElseThrow(() -> new NotFoundException(id));
		if (!Objects.equals(change.version(), user.getVersion())) {
			throw concurrentModification(user);
		}
		if (administrators.stream().anyMatch(administrator -> administrator.getId() == id)
				&& administrators.size() == 1) {
			throw lastAdministrator();
		}
		Map<String, String> before = auditValues(user);
		changeEmployee(user, change);
		user.setFullName(change.fullName());
		user.setRole(change.role().realmName());
		try {
			users.saveAndFlush(user);
		}
		catch (OptimisticLockingFailureException ex) {
			throw concurrentModification(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Another user was linked to the same employee between the check and this write
			throw NpdLeadEmployeeService.notLinkable(NpdLeadEmployeeService.HAS_USER, null, user.getEmployeeId(),
					user.getFullName());
		}
		audit.recordChanges(AUDIT_ENTITY, id, before, auditValues(user), change.actor());
		return queries.toResponse(user);
	}

	/**
	 * The employee side of the edit (design §6, "What happens by case"): a new NPD lead gets their record
	 * (CA-10), a former one goes back to Activo (CA-10), one leaving the role goes on Baja (CA-11), and a
	 * linked record gets the new name (HU-11 CA-04).
	 */
	private void changeEmployee(AppUser user, Change change) {
		boolean wasLead = NPD_LEADER.equals(user.getRole());
		boolean isLead = change.role() == Role.NPD_LEADER;
		Long employeeId = user.getEmployeeId();
		if (isLead && !wasLead && employeeId == null && change.newLead().linkEmployeeId() != null) {
			// Linked as it is: only an empty email changes
			npdLeads.link(change.newLead().linkEmployeeId(), user.getEmail(), change.actor());
			user.setEmployeeId(change.newLead().linkEmployeeId());
			return;
		}
		if (isLead && !wasLead && employeeId == null) {
			// Registered with the new name already
			user.setEmployeeId(npdLeads.register(change.newLead().register()));
			return;
		}
		if (isLead && !wasLead) {
			npdLeads.returnToRole(employeeId, change.actor());
		}
		else if (wasLead && !isLead) {
			npdLeads.leaveRole(employeeId, change.actor());
		}
		if (employeeId != null && !change.fullName().equals(user.getFullName())) {
			npdLeads.rename(employeeId, change.fullName(), change.actor());
		}
	}

	/**
	 * Puts the old name and role back in Keycloak after a failed edit, best effort: if it fails, the
	 * account is logged to fix it by hand. Closed sessions are not undone; the user just logs in again.
	 */
	private void undo(Edit edit) {
		if (edit.roleTouched) {
			undoStep(edit, "role", () -> keycloak.replaceRealmRole(edit.keycloakId, Role.ofRealmName(edit.role).orElseThrow()));
		}
		if (edit.renamed) {
			undoStep(edit, "name", () -> keycloak.rename(edit.keycloakId, edit.fullName));
		}
	}

	/** One step of the undo; a failed one does not stop the next. */
	private static void undoStep(Edit edit, String what, Runnable step) {
		try {
			step.run();
		}
		catch (RuntimeException ex) {
			log.error("Could not put the old {} back in Keycloak account {}: {}", what, edit.keycloakId,
					ex.getClass().getName());
		}
	}

	private static Map<String, String> auditValues(AppUser user) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("fullName", user.getFullName());
		values.put("role", user.getRole());
		if (user.getEmployeeId() != null) {
			values.put("employeeId", user.getEmployeeId().toString());
		}
		return values;
	}

	private static ApiException concurrentModification(AppUser current) {
		return new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(),
				Map.of("currentVersion", current.getVersion()));
	}

	private static ApiException lastAdministrator() {
		return new ApiException(ErrorCode.LAST_OPERATIONS_ADMINISTRATOR, List.of(), Map.of());
	}

	/** What the transaction saves. */
	private record Change(long id, Integer version, String fullName, Role role, boolean demotesAdministrator,
			NewLead newLead, String actor) {
	}

	/** The employee record of a new NPD lead: one to register, or an existing one to link. */
	private record NewLead(NewNpdLeadCommand register, Long linkEmployeeId) {
	}

	/** What was there before the edit and what Keycloak already changed, to undo it. */
	private static final class Edit {

		final UUID keycloakId;

		final String fullName;

		final String role;

		boolean renamed;

		boolean roleTouched;

		Edit(UUID keycloakId, String fullName, String role) {
			this.keycloakId = keycloakId;
			this.fullName = fullName;
			this.role = role;
		}

	}

}
