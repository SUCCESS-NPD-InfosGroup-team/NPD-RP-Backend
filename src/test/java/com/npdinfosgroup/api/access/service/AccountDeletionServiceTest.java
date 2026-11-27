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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.access.service.AccountDeletionService.PendingDeletion;
import com.npdinfosgroup.api.history.service.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Deleting a deactivated account (CA-08), with Keycloak and the database mocked: only a deactivated
 * account whose day has passed is deleted, Keycloak first, and its name and email stay in the audit.
 */
class AccountDeletionServiceTest {

	private static final long ID = 7L;

	private static final UUID KEYCLOAK_ID = UUID.fromString("5f0c2b8e-1d2a-4c3b-9e4f-6a7b8c9d0e1f");

	private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");

	private final AppUserRepository users = mock();

	private final KeycloakAdminClient keycloak = mock();

	private final AuditService audit = mock();

	private final AccountDeletionService service = new AccountDeletionService(users, keycloak, audit,
			TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC),
			new AccountDeletionProperties(Duration.ofDays(1), Duration.ofMinutes(5)));

	/** Due: deleted from Keycloak, then from app_user, then audited with its name, email and role. */
	@Test
	void deletesADueAccount() {
		AppUser user = given(AppUserState.DISABLED, NOW.minus(Duration.ofDays(1)));

		assertThat(service.deleteIfDue(ID)).isTrue();

		InOrder order = inOrder(keycloak, users, audit);
		order.verify(keycloak).deleteAccount(KEYCLOAK_ID);
		order.verify(users).delete(user);
		order.verify(audit).recordDeletion("app_user", ID,
				Map.of("fullName", "Ana Torres", "email", "ana@example.com", "role", "guest"), "system");
	}

	/** Before its time (a stale task, or deactivated again later) nothing is deleted. */
	@Test
	void keepsAnAccountNotDueYet() {
		given(AppUserState.DISABLED, NOW.minus(Duration.ofHours(23)));

		assertThat(service.deleteIfDue(ID)).isFalse();

		verifyNoInteractions(keycloak, audit);
		verify(users, never()).delete(any());
	}

	/** A reactivated account is never deleted. */
	@Test
	void keepsAReactivatedAccount() {
		given(AppUserState.ACTIVE, null);

		assertThat(service.deleteIfDue(ID)).isFalse();

		verifyNoInteractions(keycloak, audit);
	}

	/** An account already deleted is nothing to do. */
	@Test
	void ignoresAnAccountAlreadyDeleted() {
		when(users.lockById(ID)).thenReturn(Optional.empty());

		assertThat(service.deleteIfDue(ID)).isFalse();

		verifyNoInteractions(keycloak, audit);
	}

	/** Keycloak down: the error goes to the scheduler to retry, and the row stays. */
	@Test
	void keepsTheRowWhenKeycloakIsDown() {
		given(AppUserState.DISABLED, NOW.minus(Duration.ofDays(2)));
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.deleteAccount(KEYCLOAK_ID);

		assertThatThrownBy(() -> service.deleteIfDue(ID)).isInstanceOf(IdentityProviderUnavailableException.class);

		verify(users, never()).delete(any());
		verifyNoInteractions(audit);
	}

	/** On start, every deactivated account is listed with its deletion time. */
	@Test
	void listsTheDeactivatedAccounts() {
		AppUser user = user(AppUserState.DISABLED, NOW.minus(Duration.ofHours(2)));
		when(users.findByState(AppUserState.DISABLED)).thenReturn(List.of(user));

		assertThat(service.pending()).containsExactly(new PendingDeletion(ID, NOW.plus(Duration.ofHours(22))));
	}

	private AppUser given(AppUserState state, Instant disabledAt) {
		AppUser user = user(state, disabledAt);
		when(users.lockById(ID)).thenReturn(Optional.of(user));
		return user;
	}

	private static AppUser user(AppUserState state, Instant disabledAt) {
		AppUser user = new AppUser();
		user.setId(ID);
		user.setKeycloakId(KEYCLOAK_ID);
		user.setFullName("Ana Torres");
		user.setEmail("ana@example.com");
		user.setRole("guest");
		user.setState(state);
		user.setDisabledAt(disabledAt);
		return user;
	}

}
