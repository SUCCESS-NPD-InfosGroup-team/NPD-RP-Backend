package com.npdinfosgroup.api.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.dto.request.UpdateUserCommand;
import com.npdinfosgroup.api.access.dto.request.UpdateUserRequest;
import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.access.service.validator.UpdateUserRequestValidator;
import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import com.npdinfosgroup.api.shared.security.Role;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Editing a user, with Keycloak and the database mocked: what is called and in which order, the checks
 * of CA-05 and CA-13, and the version.
 */
class UserEditServiceTest {

	private static final long ID = 7L;

	private static final UUID KEYCLOAK_ID = UUID.fromString("5f0c2b8e-1d2a-4c3b-9e4f-6a7b8c9d0e1f");

	private final AppUserRepository users = mock();

	private final KeycloakAdminClient keycloak = mock();

	private final AuditService audit = mock();

	private final UpdateUserRequestValidator validator = mock();

	private final UserQueryService queries = mock();

	private final UserEditService service = new UserEditService(validator, users, keycloak, audit,
			TransactionOperations.withoutTransaction(), queries);

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

	/** Until the NPD lead cases exist, the role cannot change to NPD lead. */
	@Test
	void rejectsTheChangeToNpdLeadForNow() {
		when(users.findById(ID)).thenReturn(Optional.of(user("Ana Ríos", "guest", AppUserState.ACTIVE)));
		when(validator.validate(any())).thenReturn(Role.NPD_LEADER);

		assertThatThrownBy(() -> service.update(command("Ana Ríos", Role.NPD_LEADER)))
			.isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getProblems())
				.singleElement()
				.satisfies(problem -> assertThat(problem.code()).isEqualTo(FieldErrorCode.ROLE_NOT_ASSIGNABLE)));
		verifyNoInteractions(keycloak);
	}

	/** Someone saved the user while Keycloak was called: the recheck in the transaction gives 409. */
	@Test
	void rejectsAVersionChangedMeanwhile() {
		AppUser locked = given(user("Ana Ríos", "guest", AppUserState.ACTIVE));
		locked.setVersion(1);

		assertThatThrownBy(() -> service.update(command("Ana María", Role.CUSTOMER_SUCCESS)))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
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
	}

	private UpdateUserCommand command(String fullName, Role role) {
		return new UpdateUserCommand(ID, new UpdateUserRequest(0, fullName, role.realmName()), "admin@example.com");
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
