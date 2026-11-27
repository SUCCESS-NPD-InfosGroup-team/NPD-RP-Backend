package com.npdinfosgroup.api.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.dto.request.AccountActivationCommand;
import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import com.npdinfosgroup.api.shared.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Deactivating an account, with Keycloak and the database mocked: what is called and in which order,
 * the version, and how a failure is undone in Keycloak.
 */
class AccountActivationServiceTest {

	private static final long ID = 7L;

	private static final String ACTOR = "admin@example.com";

	private static final UUID KEYCLOAK_ID = UUID.fromString("5f0c2b8e-1d2a-4c3b-9e4f-6a7b8c9d0e1f");

	private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");

	private final AppUserRepository users = mock();

	private final KeycloakAdminClient keycloak = mock();

	private final AuditService audit = mock();

	private final UserQueryService queries = mock();

	private final AccountActivationService service = new AccountActivationService(users, keycloak, audit,
			TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC), queries);

	private final UserResponse response = mock();

	/**
	 * CA-01: disabled in Keycloak, sessions closed, then saved DISABLED with the moment and audited. A
	 * pending account (invitation never used) can be deactivated too.
	 */
	@ParameterizedTest
	@EnumSource(names = { "ACTIVE", "PENDING" })
	void deactivatesTheAccount(AppUserState state) {
		AppUser locked = given(user("guest", state));

		assertThat(service.deactivate(command(0))).isSameAs(response);

		InOrder order = inOrder(keycloak, users, audit);
		order.verify(keycloak).setEnabled(KEYCLOAK_ID, false);
		order.verify(keycloak).logout(KEYCLOAK_ID);
		order.verify(users).saveAndFlush(locked);
		order.verify(audit).recordChanges("app_user", ID, Map.of("state", state.name()), Map.of("state", "DISABLED"), ACTOR);
		assertThat(locked.getState()).isEqualTo(AppUserState.DISABLED);
		assertThat(locked.getDisabledAt()).isEqualTo(NOW);
	}

	/** Deactivating again changes nothing and calls nothing. */
	@Test
	void returnsAnAccountAlreadyDeactivatedAsItIs() {
		AppUser user = user("guest", AppUserState.DISABLED);
		when(users.findById(ID)).thenReturn(Optional.of(user));
		when(queries.toResponse(user)).thenReturn(response);

		assertThat(service.deactivate(command(0))).isSameAs(response);

		verifyNoInteractions(keycloak, audit);
		verify(users, never()).saveAndFlush(any());
	}

	/** The version is required. */
	@Test
	void requiresTheVersion() {
		assertThatThrownBy(() -> service.deactivate(new AccountActivationCommand(ID, null, ACTOR)))
			.isInstanceOf(ValidationException.class);
		verifyNoInteractions(users, keycloak);
	}

	/** A modal read before the last save is rejected before anything is called. */
	@Test
	void rejectsAStaleVersion() {
		AppUser user = user("guest", AppUserState.ACTIVE);
		user.setVersion(4);
		when(users.findById(ID)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.deactivate(command(3))).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
			assertThat(ex.getProperties()).containsEntry("currentVersion", 4);
		});
		verifyNoInteractions(keycloak);
	}

	/** A save after the first read, seen under the lock, is a concurrent modification; Keycloak is undone. */
	@Test
	void rejectsAVersionChangedWhileKeycloakWasCalled() {
		AppUser locked = given(user("guest", AppUserState.ACTIVE));
		locked.setVersion(1);

		assertThatThrownBy(() -> service.deactivate(command(0))).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
		verify(keycloak).setEnabled(KEYCLOAK_ID, true);
	}

	/** The optimistic lock of the save is a concurrent modification too. */
	@Test
	void reportsTheOptimisticLockOfTheSave() {
		AppUser locked = given(user("guest", AppUserState.ACTIVE));
		when(users.saveAndFlush(locked)).thenThrow(new OptimisticLockingFailureException("stale"));

		assertThatThrownBy(() -> service.deactivate(command(0))).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
		verify(keycloak).setEnabled(KEYCLOAK_ID, true);
		verifyNoInteractions(audit);
	}

	/** Keycloak down while disabling: it is enabled again (best effort) and the outage is reported. */
	@Test
	void undoesTheDeactivationWhenKeycloakFails() {
		given(user("guest", AppUserState.ACTIVE));
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak).logout(KEYCLOAK_ID);

		assertThatThrownBy(() -> service.deactivate(command(0))).isInstanceOf(IdentityProviderUnavailableException.class);

		verify(keycloak).setEnabled(KEYCLOAK_ID, true);
		verify(users, never()).saveAndFlush(any());
	}

	/** A failed undo is only logged: the original error is the one reported. */
	@Test
	void reportsTheOriginalErrorWhenTheUndoFails() {
		AppUser locked = given(user("guest", AppUserState.ACTIVE));
		when(users.saveAndFlush(locked)).thenThrow(new IllegalStateException("database"));
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.setEnabled(KEYCLOAK_ID, true);

		assertThatThrownBy(() -> service.deactivate(command(0))).isInstanceOf(IllegalStateException.class)
			.hasMessage("database");
	}

	/** An unknown user is a 404, with no call to Keycloak. */
	@Test
	void rejectsAnUnknownUser() {
		when(users.findById(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deactivate(command(0))).isInstanceOf(NotFoundException.class);
		verify(keycloak, never()).setEnabled(any(), anyBoolean());
	}

	/** CA-09: an NPD lead's employee record is left as it is; only the user changes. */
	@Test
	void keepsTheEmployeeRecordOfAnNpdLead() {
		AppUser user = user(Role.NPD_LEADER.realmName(), AppUserState.ACTIVE);
		user.setEmployeeId(42L);
		AppUser locked = given(user);

		service.deactivate(command(0));

		assertThat(locked.getEmployeeId()).isEqualTo(42L);
		assertThat(locked.getState()).isEqualTo(AppUserState.DISABLED);
	}

	private AccountActivationCommand command(int version) {
		return new AccountActivationCommand(ID, version, ACTOR);
	}

	private AppUser given(AppUser user) {
		AppUser locked = user(user.getRole(), user.getState());
		locked.setEmployeeId(user.getEmployeeId());
		when(users.findById(ID)).thenReturn(Optional.of(user));
		when(users.lockById(ID)).thenReturn(Optional.of(locked));
		when(queries.toResponse(locked)).thenReturn(response);
		return locked;
	}

	private static AppUser user(String role, AppUserState state) {
		AppUser user = other(ID);
		user.setKeycloakId(KEYCLOAK_ID);
		user.setFullName("Ana Torres");
		user.setEmail("ana@example.com");
		user.setRole(role);
		user.setState(state);
		return user;
	}

	private static AppUser other(long id) {
		AppUser user = new AppUser();
		user.setId(id);
		user.setVersion(0);
		return user;
	}

}
