package com.npdinfosgroup.api.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
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
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Reactivating an account (CA-02), with Keycloak and the database mocked: the state it goes back to, the
 * deletion time, the version, and how a failure is undone in Keycloak.
 */
class AccountReactivationServiceTest {

	private static final long ID = 7L;

	private static final String ACTOR = "admin@example.com";

	private static final UUID KEYCLOAK_ID = UUID.fromString("5f0c2b8e-1d2a-4c3b-9e4f-6a7b8c9d0e1f");

	private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");

	/** Deactivated 23 hours ago: one hour left. */
	private static final Instant DISABLED_AT = NOW.minus(Duration.ofHours(23));

	private final AppUserRepository users = mock();

	private final KeycloakAdminClient keycloak = mock();

	private final AuditService audit = mock();

	private final UserQueryService queries = mock();

	private final AccountActivationService service = new AccountActivationService(users, keycloak, audit,
			TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC), queries,
			new AccountDeletionProperties(Duration.ofDays(1), Duration.ofMinutes(5)));

	private final UserResponse response = mock();

	/** CA-02: enabled in Keycloak, then back to ACTIVE without the deletion, and audited. */
	@Test
	void reactivatesAnAccountThatWasUsed() {
		AppUser locked = given(disabled(Instant.parse("2026-11-20T10:00:00Z")));

		assertThat(service.reactivate(command(0))).isSameAs(response);

		InOrder order = inOrder(keycloak, users, audit);
		order.verify(keycloak).setEnabled(KEYCLOAK_ID, true);
		order.verify(users).saveAndFlush(locked);
		order.verify(audit).recordChanges("app_user", ID, Map.of("state", "DISABLED"), Map.of("state", "ACTIVE"), ACTOR);
		assertThat(locked.getState()).isEqualTo(AppUserState.ACTIVE);
		assertThat(locked.getDisabledAt()).isNull();
		verify(keycloak, never()).logout(any());
	}

	/** An account that never accessed the platform goes back to PENDING. */
	@Test
	void reactivatesAPendingAccount() {
		AppUser locked = given(disabled(null));

		service.reactivate(command(0));

		assertThat(locked.getState()).isEqualTo(AppUserState.PENDING);
		verify(audit).recordChanges("app_user", ID, Map.of("state", "DISABLED"), Map.of("state", "PENDING"), ACTOR);
	}

	/** An account that is not deactivated is returned as it is, with no calls. */
	@Test
	void returnsAnAccountNotDeactivatedAsItIs() {
		AppUser user = user(AppUserState.ACTIVE, null);
		when(users.findById(ID)).thenReturn(Optional.of(user));
		when(queries.toResponse(user)).thenReturn(response);

		assertThat(service.reactivate(command(0))).isSameAs(response);

		verifyNoInteractions(keycloak, audit);
	}

	/** Past the deletion time it is rejected, even if the deletion has not run yet; nothing is called. */
	@Test
	void rejectsAnAccountPastItsDeletionTime() {
		AppUser user = user(AppUserState.DISABLED, NOW.minus(Duration.ofDays(1)));
		when(users.findById(ID)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.reactivate(command(0))).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getCode()).isEqualTo(ErrorCode.ACCOUNT_DELETION_DUE);
			assertThat(ex.getProperties()).containsEntry("deletesAt", NOW);
		});
		verifyNoInteractions(keycloak);
	}

	/** The deletion time passed while Keycloak was called: rejected under the lock, disabled again. */
	@Test
	void rejectsADeletionTimeReachedUnderTheLock() {
		AppUser locked = given(disabled(null));
		locked.setDisabledAt(NOW.minus(Duration.ofDays(2)));

		assertThatThrownBy(() -> service.reactivate(command(0))).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.ACCOUNT_DELETION_DUE));
		verify(keycloak).setEnabled(KEYCLOAK_ID, false);
		verify(users, never()).saveAndFlush(any());
	}

	/** A save after the first read, seen under the lock, is a concurrent modification. */
	@Test
	void rejectsAVersionChangedWhileKeycloakWasCalled() {
		AppUser locked = given(disabled(null));
		locked.setVersion(2);

		assertThatThrownBy(() -> service.reactivate(command(0))).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
		verify(keycloak).setEnabled(KEYCLOAK_ID, false);
	}

	/** The optimistic lock of the save is a concurrent modification too; the account is disabled again. */
	@Test
	void reportsTheOptimisticLockOfTheSave() {
		AppUser locked = given(disabled(null));
		when(users.saveAndFlush(locked)).thenThrow(new OptimisticLockingFailureException("stale"));

		assertThatThrownBy(() -> service.reactivate(command(0))).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
		verify(keycloak).setEnabled(KEYCLOAK_ID, false);
		verifyNoInteractions(audit);
	}

	/** Keycloak down: nothing is saved and the outage is reported. */
	@Test
	void reportsKeycloakUnavailable() {
		given(disabled(null));
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.setEnabled(KEYCLOAK_ID, true);

		assertThatThrownBy(() -> service.reactivate(command(0))).isInstanceOf(IdentityProviderUnavailableException.class);
		verify(users, never()).saveAndFlush(any());
	}

	/** The deletion time is the deactivation plus the delay; none while the account is not deactivated. */
	@Test
	void computesTheDeletionTime() {
		AccountDeletionProperties deletion = new AccountDeletionProperties(Duration.ofDays(1), Duration.ofMinutes(5));

		assertThat(deletion.deletesAt(DISABLED_AT)).isEqualTo(NOW.plus(Duration.ofHours(1)));
		assertThat(deletion.deletesAt(null)).isNull();
	}

	private AccountActivationCommand command(int version) {
		return new AccountActivationCommand(ID, version, ACTOR);
	}

	private AppUser given(AppUser user) {
		AppUser locked = user(user.getState(), user.getDisabledAt());
		locked.setLastAccessAt(user.getLastAccessAt());
		when(users.findById(ID)).thenReturn(Optional.of(user));
		when(users.lockById(ID)).thenReturn(Optional.of(locked));
		when(queries.toResponse(locked)).thenReturn(response);
		return locked;
	}

	private static AppUser disabled(Instant lastAccessAt) {
		AppUser user = user(AppUserState.DISABLED, DISABLED_AT);
		user.setLastAccessAt(lastAccessAt);
		return user;
	}

	private static AppUser user(AppUserState state, Instant disabledAt) {
		AppUser user = new AppUser();
		user.setId(ID);
		user.setVersion(0);
		user.setKeycloakId(KEYCLOAK_ID);
		user.setRole("guest");
		user.setState(state);
		user.setDisabledAt(disabledAt);
		return user;
	}

}
