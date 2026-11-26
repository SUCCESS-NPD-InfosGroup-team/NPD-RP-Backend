package com.npdinfosgroup.api.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.dto.request.UpdateUserCommand;
import com.npdinfosgroup.api.access.dto.request.UpdateUserRequest;
import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.access.service.validator.UpdateUserRequestValidator;
import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.NewNpdLeadCommand;
import com.npdinfosgroup.api.planner.dto.request.NpdLeadEmployeeRequest;
import com.npdinfosgroup.api.planner.service.NpdLeadEmployeeService;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import com.npdinfosgroup.api.shared.security.Role;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Editing a user, with Keycloak and the database mocked: what is called and in which order, the checks
 * of CA-04, CA-05 and CA-13, the version, and how a failure is undone in Keycloak.
 */
class UserEditServiceTest {

	private static final long ID = 7L;

	private static final String ACTOR = "admin@example.com";

	private static final NpdLeadEmployeeRequest EMPLOYEE = new NpdLeadEmployeeRequest("1105", (short) 1, 3L, (short) 2,
			LocalDate.of(2026, 3, 3), "ACTIVO");

	private static final UUID KEYCLOAK_ID = UUID.fromString("5f0c2b8e-1d2a-4c3b-9e4f-6a7b8c9d0e1f");

	private static final String ADMINISTRATOR = Role.OPERATIONS_ADMINISTRATOR.realmName();

	private final AppUserRepository users = mock();

	private final KeycloakAdminClient keycloak = mock();

	private final AuditService audit = mock();

	private final UpdateUserRequestValidator validator = mock();

	private final UserQueryService queries = mock();

	private final NpdLeadEmployeeService npdLeads = mock();

	private final UserEditService service = new UserEditService(validator, users, keycloak, audit,
			TransactionOperations.withoutTransaction(), queries, npdLeads);

	private final UserResponse response = mock();

	/** A new name is set in Keycloak and saved with its audit; the sessions stay open. */
	@Test
	void renamesTheUser() {
		AppUser locked = given(user("Ana Ríos", "guest", AppUserState.ACTIVE));

		assertThat(service.update(command(" Ana María Ríos ", Role.GUEST))).isSameAs(response);

		verify(keycloak).rename(KEYCLOAK_ID, "Ana María Ríos");
		verify(keycloak, never()).replaceRealmRole(any(), any());
		verify(keycloak, never()).logout(any());
		assertThat(locked.getFullName()).isEqualTo("Ana María Ríos");
		verify(audit).recordChanges("app_user", ID, Map.of("fullName", "Ana Ríos", "role", "guest"),
				Map.of("fullName", "Ana María Ríos", "role", "guest"), "admin@example.com");
	}

	/** A new role replaces the old one in Keycloak, then the sessions are closed, then the row is saved. */
	@Test
	void changesTheRoleAndClosesTheSessions() {
		AppUser locked = given(user("Ana Ríos", "guest", AppUserState.ACTIVE));

		service.update(command("Ana Ríos", Role.CUSTOMER_SUCCESS));

		InOrder order = inOrder(keycloak, users, audit);
		order.verify(keycloak).replaceRealmRole(KEYCLOAK_ID, Role.CUSTOMER_SUCCESS);
		order.verify(keycloak).logout(KEYCLOAK_ID);
		order.verify(users).saveAndFlush(locked);
		order.verify(audit).recordChanges(eq("app_user"), eq(ID), any(), any(), eq("admin@example.com"));
		verify(keycloak, never()).rename(any(), any());
		assertThat(locked.getRole()).isEqualTo("customer-success");
	}

	/** CA-05: the same values (spaces aside) call nothing and save nothing. */
	@Test
	void changesNothingWhenTheValuesAreTheSame() {
		AppUser user = user("Ana Ríos", "guest", AppUserState.ACTIVE);
		when(users.findById(ID)).thenReturn(Optional.of(user));
		when(validator.validate(any())).thenReturn(Role.GUEST);
		when(queries.toResponse(user)).thenReturn(response);

		assertThat(service.update(command("  Ana Ríos ", Role.GUEST))).isSameAs(response);

		verifyNoInteractions(keycloak, audit);
		verify(users, never()).saveAndFlush(any());
	}

	/** A form read before the last save is rejected before its fields are checked. */
	@Test
	void rejectsAStaleVersion() {
		AppUser user = user("Ana Ríos", "guest", AppUserState.ACTIVE);
		user.setVersion(4);
		when(users.findById(ID)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.update(command("Ana", Role.GUEST)))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
				assertThat(ex.getProperties()).containsEntry("currentVersion", 4);
			});
		verifyNoInteractions(validator, keycloak);
	}

	/** An unknown user is a 404. */
	@Test
	void rejectsAnUnknownUser() {
		when(users.findById(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(command("Ana", Role.GUEST))).isInstanceOf(NotFoundException.class);
	}

	/** CA-13: an NPD lead without employee record cannot be edited, not even its name. */
	@Test
	void rejectsAnNpdLeadWithoutEmployeeRecord() {
		when(users.findById(ID)).thenReturn(Optional.of(user("Ana Ríos", "npd-leader", AppUserState.ACTIVE)));
		when(validator.validate(any())).thenReturn(Role.NPD_LEADER);

		assertThatThrownBy(() -> service.update(command("Ana María", Role.NPD_LEADER)))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NPD_LEAD_WITHOUT_EMPLOYEE));
		verifyNoInteractions(keycloak);
	}

	/**
	 * CA-10: a user without employee record becomes NPD lead: the record is registered with the user's
	 * new name and email, and linked.
	 */
	@Test
	void registersTheRecordOfANewNpdLead() {
		AppUser locked = given(user("Ana Ríos", "guest", AppUserState.ACTIVE));
		when(npdLeads.register(any())).thenReturn(42L);

		service.update(new UpdateUserCommand(ID, new UpdateUserRequest(0, "Ana María", "npd-leader", EMPLOYEE, null), ACTOR));

		NewNpdLeadCommand lead = new NewNpdLeadCommand(EMPLOYEE, "Ana María", "ana@example.com", ACTOR);
		verify(npdLeads).problemsOf(lead);
		verify(npdLeads).register(lead);
		verify(npdLeads, never()).rename(anyLong(), any(), any());
		assertThat(locked.getEmployeeId()).isEqualTo(42L);
		verify(audit).recordChanges(eq("app_user"), eq(ID), any(), eq(Map.of("fullName", "Ana María", "role",
				"npd-leader", "employeeId", "42")), eq(ACTOR));
	}

	/** CA-10: the employee errors come with the user's, before anything is called. */
	@Test
	void reportsTheEmployeeErrorsWithTheUserOnes() {
		when(users.findById(ID)).thenReturn(Optional.of(user("Ana Ríos", "guest", AppUserState.ACTIVE)));
		when(validator.validate(any()))
			.thenThrow(new ValidationException("user", List.of(FieldProblem.of("fullName", FieldErrorCode.TOO_LONG))));
		when(npdLeads.problemsOf(any()))
			.thenReturn(List.of(FieldProblem.of("employee.genderId", FieldErrorCode.REQUIRED)));

		assertThatThrownBy(() -> service.update(new UpdateUserCommand(ID,
				new UpdateUserRequest(0, "Ana", "npd-leader", EMPLOYEE, null), ACTOR)))
			.isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getProblems())
				.extracting(FieldProblem::field)
				.containsExactly("fullName", "employee.genderId"));
		verifyNoInteractions(keycloak);
	}

	/** CA-04: the last active administrator cannot lose the role; nothing reaches Keycloak. */
	@Test
	void keepsTheLastAdministrator() {
		when(users.findById(ID)).thenReturn(Optional.of(user("Ana Ríos", ADMINISTRATOR, AppUserState.ACTIVE)));
		when(validator.validate(any())).thenReturn(Role.GUEST);
		when(users.countByRoleAndStateAndIdNot(ADMINISTRATOR, AppUserState.ACTIVE, ID)).thenReturn(0L);

		assertThatThrownBy(() -> service.update(command("Ana Ríos", Role.GUEST)))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.LAST_OPERATIONS_ADMINISTRATOR));
		verifyNoInteractions(keycloak);
	}

	/** A pending administrator does not count, so demoting one never leaves the platform without any. */
	@Test
	void demotesAPendingAdministratorWithoutCounting() {
		given(user("Ana Ríos", ADMINISTRATOR, AppUserState.PENDING));
		when(users.lockByRoleAndState(ADMINISTRATOR, AppUserState.ACTIVE)).thenReturn(List.of(user(99L)));

		service.update(command("Ana Ríos", Role.GUEST));

		verify(users, never()).countByRoleAndStateAndIdNot(anyString(), any(), anyLong());
		verify(keycloak).replaceRealmRole(KEYCLOAK_ID, Role.GUEST);
	}

	/**
	 * Another administrator demoted the other one while Keycloak was called: the recheck, with the
	 * administrators locked, rejects the edit and the old role is put back.
	 */
	@Test
	void rechecksTheLastAdministratorWithTheLock() {
		given(user("Ana Ríos", ADMINISTRATOR, AppUserState.ACTIVE));
		when(users.countByRoleAndStateAndIdNot(ADMINISTRATOR, AppUserState.ACTIVE, ID)).thenReturn(1L);
		when(users.lockByRoleAndState(ADMINISTRATOR, AppUserState.ACTIVE)).thenReturn(List.of(user(ID)));

		assertThatThrownBy(() -> service.update(command("Ana Ríos", Role.GUEST)))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.LAST_OPERATIONS_ADMINISTRATOR));
		InOrder order = inOrder(users, keycloak);
		order.verify(users).lockByRoleAndState(ADMINISTRATOR, AppUserState.ACTIVE);
		order.verify(users).lockById(ID);
		order.verify(keycloak).replaceRealmRole(KEYCLOAK_ID, Role.OPERATIONS_ADMINISTRATOR);
		verify(users, never()).saveAndFlush(any());
	}

	/** With another active administrator the role changes. */
	@Test
	void demotesAnAdministratorWhenAnotherRemains() {
		AppUser locked = given(user("Ana Ríos", ADMINISTRATOR, AppUserState.ACTIVE));
		when(users.countByRoleAndStateAndIdNot(ADMINISTRATOR, AppUserState.ACTIVE, ID)).thenReturn(1L);
		when(users.lockByRoleAndState(ADMINISTRATOR, AppUserState.ACTIVE)).thenReturn(List.of(user(ID), user(99L)));

		service.update(command("Ana Ríos", Role.GUEST));

		assertThat(locked.getRole()).isEqualTo("guest");
	}

	/** Someone saved the user while Keycloak was called: 409, and Keycloak gets the old values back. */
	@Test
	void undoesKeycloakWhenTheVersionChangedMeanwhile() {
		AppUser locked = given(user("Ana Ríos", "guest", AppUserState.ACTIVE));
		locked.setVersion(1);

		assertThatThrownBy(() -> service.update(command("Ana María", Role.CUSTOMER_SUCCESS)))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
		verify(keycloak).replaceRealmRole(KEYCLOAK_ID, Role.GUEST);
		verify(keycloak).rename(KEYCLOAK_ID, "Ana Ríos");
		verify(users, never()).saveAndFlush(any());
	}

	/** A concurrent write caught by the version on flush is a 409 too. */
	@Test
	void reportsAConcurrentWriteOnFlush() {
		AppUser locked = given(user("Ana Ríos", "guest", AppUserState.ACTIVE));
		when(users.saveAndFlush(locked)).thenThrow(new OptimisticLockingFailureException("stale"));

		assertThatThrownBy(() -> service.update(command("Ana María", Role.GUEST)))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
		verify(keycloak).rename(KEYCLOAK_ID, "Ana Ríos");
	}

	/**
	 * The new role could not be given (the old one may be gone already): the old role is put back, and
	 * the name too.
	 */
	@Test
	void undoesTheRoleWhenReplacingItFails() {
		given(user("Ana Ríos", "guest", AppUserState.ACTIVE));
		IdentityProviderUnavailableException outage = new IdentityProviderUnavailableException(new RuntimeException());
		doThrow(outage).when(keycloak).replaceRealmRole(KEYCLOAK_ID, Role.CUSTOMER_SUCCESS);

		assertThatThrownBy(() -> service.update(command("Ana María", Role.CUSTOMER_SUCCESS))).isSameAs(outage);

		verify(keycloak).replaceRealmRole(KEYCLOAK_ID, Role.GUEST);
		verify(keycloak).rename(KEYCLOAK_ID, "Ana Ríos");
		verify(keycloak, never()).logout(any());
		verify(users, never()).lockById(anyLong());
	}

	/** A rename that fails changed nothing: there is nothing to undo. */
	@Test
	void undoesNothingWhenTheRenameFails() {
		given(user("Ana Ríos", "guest", AppUserState.ACTIVE));
		IdentityProviderUnavailableException outage = new IdentityProviderUnavailableException(new RuntimeException());
		doThrow(outage).when(keycloak).rename(KEYCLOAK_ID, "Ana María");

		assertThatThrownBy(() -> service.update(command("Ana María", Role.GUEST))).isSameAs(outage);

		verify(keycloak).rename(KEYCLOAK_ID, "Ana María");
		verify(keycloak, never()).rename(KEYCLOAK_ID, "Ana Ríos");
	}

	/** If the undo fails too, the original error is still the one returned (the undo is only logged). */
	@Test
	void returnsTheOriginalErrorWhenTheUndoFails() {
		AppUser locked = given(user("Ana Ríos", "guest", AppUserState.ACTIVE));
		RuntimeException databaseDown = new RuntimeException("database down");
		when(users.saveAndFlush(locked)).thenThrow(databaseDown);
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.rename(KEYCLOAK_ID, "Ana Ríos");

		assertThatThrownBy(() -> service.update(command("Ana María", Role.GUEST))).isSameAs(databaseDown);
	}

	/** A failed undo of the role does not stop putting the old name back. */
	@Test
	void undoesTheNameWhenUndoingTheRoleFails() {
		AppUser locked = given(user("Ana Ríos", "guest", AppUserState.ACTIVE));
		when(users.saveAndFlush(locked)).thenThrow(new RuntimeException("database down"));
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.replaceRealmRole(KEYCLOAK_ID, Role.GUEST);

		assertThatThrownBy(() -> service.update(command("Ana María", Role.CUSTOMER_SUCCESS)))
			.hasMessage("database down");
		verify(keycloak).rename(KEYCLOAK_ID, "Ana Ríos");
	}

	private UpdateUserCommand command(String fullName, Role role) {
		return new UpdateUserCommand(ID, new UpdateUserRequest(0, fullName, role.realmName(), null, null), "admin@example.com");
	}

	/** Answers the first read and the locked one (another instance, as in a new transaction); returns the latter. */
	private AppUser given(AppUser user) {
		AppUser locked = user(user.getFullName(), user.getRole(), user.getState());
		when(users.findById(ID)).thenReturn(Optional.of(user));
		when(users.lockById(ID)).thenReturn(Optional.of(locked));
		when(validator.validate(any())).thenAnswer(invocation -> Role
			.ofRealmName(invocation.<UpdateUserRequest>getArgument(0).role())
			.orElseThrow());
		when(queries.toResponse(locked)).thenReturn(response);
		return locked;
	}

	private static AppUser user(String fullName, String role, AppUserState state) {
		AppUser user = user(ID);
		user.setKeycloakId(KEYCLOAK_ID);
		user.setFullName(fullName);
		user.setEmail("ana@example.com");
		user.setRole(role);
		user.setState(state);
		return user;
	}

	private static AppUser user(long id) {
		AppUser user = new AppUser();
		user.setId(id);
		user.setVersion(0);
		return user;
	}

}
