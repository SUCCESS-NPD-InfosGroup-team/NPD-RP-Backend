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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.dto.request.UserDeletionCommand;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Deleting a user (SCRUM-231), with Keycloak and the database mocked: Keycloak first and then the row and
 * its audit, the version and the last active Operations administrator checked with the rows locked.
 */
class UserDeletionServiceTest {

	private static final long ID = 7L;

	private static final String ADMIN = "operations-administrator";

	private static final String ACTOR = "jorge@example.com";

	private final AppUserRepository users = mock();

	private final KeycloakAdminClient keycloak = mock();

	private final AuditService audit = mock();

	private final UserDeletionService service = new UserDeletionService(users, keycloak, audit,
			TransactionOperations.withoutTransaction());

	/** CA-01, CA-06: deleted from Keycloak, then from app_user, then audited with its last values. */
	@Test
	void deletesTheAccountThenTheRowThenAudits() {
		AppUser user = given(user(ID, "guest", AppUserState.ACTIVE));

		service.delete(command(0));

		InOrder order = inOrder(keycloak, users, audit);
		order.verify(users).lockById(ID);
		order.verify(keycloak).deleteAccount(user.getKeycloakId());
		order.verify(users).delete(user);
		order.verify(audit).recordDeletion("app_user", ID,
				Map.of("fullName", "Ana Torres", "email", "ana@example.com", "role", "guest"), ACTOR);
		verify(users, never()).lockByRoleAndState(any(), any());
	}

	/** CA-04: the only active Operations administrator is not deleted. */
	@Test
	void keepsTheLastActiveAdministrator() {
		AppUser admin = given(user(ID, ADMIN, AppUserState.ACTIVE));
		when(users.lockByRoleAndState(ADMIN, AppUserState.ACTIVE)).thenReturn(List.of(admin));

		assertThatThrownBy(() -> service.delete(command(0))).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.LAST_OPERATIONS_ADMINISTRATOR));

		verifyNoInteractions(keycloak, audit);
		verify(users, never()).delete(any());
	}

	/** CA-04: an administrator is deleted while another active one remains, locked before the user. */
	@Test
	void deletesAnAdministratorWhenAnotherRemains() {
		AppUser admin = given(user(ID, ADMIN, AppUserState.ACTIVE));
		when(users.lockByRoleAndState(ADMIN, AppUserState.ACTIVE)).thenReturn(List.of(admin, user(8L, ADMIN,
				AppUserState.ACTIVE)));

		service.delete(command(0));

		InOrder order = inOrder(users, keycloak);
		order.verify(users).lockByRoleAndState(ADMIN, AppUserState.ACTIVE);
		order.verify(users).lockById(ID);
		order.verify(keycloak).deleteAccount(admin.getKeycloakId());
	}

	/** A pending administrator is not the active one CA-04 protects, even with no active one left. */
	@Test
	void deletesAPendingAdministrator() {
		AppUser admin = given(user(ID, ADMIN, AppUserState.PENDING));
		when(users.lockByRoleAndState(ADMIN, AppUserState.ACTIVE)).thenReturn(List.of());

		service.delete(command(0));

		verify(keycloak).deleteAccount(admin.getKeycloakId());
	}

	/** A user saved after it was read is not deleted. */
	@Test
	void rejectsAStaleVersion() {
		given(user(ID, "guest", AppUserState.ACTIVE)).setVersion(2);

		assertThatThrownBy(() -> service.delete(command(1))).isInstanceOfSatisfying(ApiException.class, ex -> {
			assertThat(ex.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
			assertThat(ex.getProperties()).containsEntry("currentVersion", 2);
		});
		verifyNoInteractions(keycloak, audit);
	}

	/** The version is required, checked before anything is read. */
	@Test
	void requiresTheVersion() {
		assertThatThrownBy(() -> service.delete(new UserDeletionCommand(ID, null, ACTOR)))
			.isInstanceOf(ValidationException.class);

		verifyNoInteractions(users, keycloak, audit);
	}

	/** An unknown user, or one already deleted, is a 404. */
	@Test
	void rejectsAnUnknownUser() {
		when(users.findById(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(command(0))).isInstanceOf(NotFoundException.class);

		verifyNoInteractions(keycloak, audit);
	}

	/** Keycloak down: the error goes up and the row stays. */
	@Test
	void keepsTheRowWhenKeycloakIsDown() {
		AppUser user = given(user(ID, "guest", AppUserState.ACTIVE));
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.deleteAccount(user.getKeycloakId());

		assertThatThrownBy(() -> service.delete(command(0))).isInstanceOf(IdentityProviderUnavailableException.class);

		verify(users, never()).delete(any());
		verifyNoInteractions(audit);
	}

	private AppUser given(AppUser user) {
		when(users.findById(ID)).thenReturn(Optional.of(user));
		when(users.lockById(ID)).thenReturn(Optional.of(user));
		return user;
	}

	private static UserDeletionCommand command(int version) {
		return new UserDeletionCommand(ID, version, ACTOR);
	}

	private static AppUser user(long id, String role, AppUserState state) {
		AppUser user = new AppUser();
		user.setId(id);
		user.setKeycloakId(UUID.randomUUID());
		user.setFullName("Ana Torres");
		user.setEmail("ana@example.com");
		user.setRole(role);
		user.setState(state);
		user.setVersion(0);
		return user;
	}

}
