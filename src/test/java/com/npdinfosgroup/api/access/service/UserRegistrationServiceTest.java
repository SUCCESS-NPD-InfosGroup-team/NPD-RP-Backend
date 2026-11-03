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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.client.KeycloakUserExistsException;
import com.npdinfosgroup.api.access.dto.request.CreateUserCommand;
import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Creating a user, with Keycloak and the database mocked: call order, saved row, audit, duplicate email, rollback.
 */
class UserRegistrationServiceTest {

	private static final UUID KEYCLOAK_ID = UUID.fromString("5f0c2b8e-1d2a-4c3b-9e4f-6a7b8c9d0e1f");

	private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

	private final AppUserRepository users = mock();

	private final KeycloakAdminClient keycloak = mock();

	private final AuditService audit = mock();

	private final UserRegistrationService service = new UserRegistrationService(users, keycloak, audit,
			TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC));

	private final CreateUserCommand command = new CreateUserCommand(" Ana María Ríos ", " Ana.Rios@Example.com ",
			Role.NPD_LEADER, "admin");

	/**
	 * The account is created, gets its role and the invitation, in that order; the user is saved as
	 * pending with the email in lower case, and its creation is audited.
	 */
	@Test
	void registersTheUser() {
		when(keycloak.createUser("ana.rios@example.com", "Ana María Ríos")).thenReturn(KEYCLOAK_ID);
		when(users.save(any())).thenAnswer(invocation -> {
			invocation.<AppUser>getArgument(0).setId(7L);
			return invocation.getArgument(0);
		});

		UserResponse response = service.register(command);

		InOrder order = inOrder(keycloak);
		order.verify(keycloak).createUser("ana.rios@example.com", "Ana María Ríos");
		order.verify(keycloak).assignRealmRole(KEYCLOAK_ID, Role.NPD_LEADER);
		order.verify(keycloak).sendInvitation(KEYCLOAK_ID);
		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue()).extracting(AppUser::getKeycloakId, AppUser::getState, AppUser::getCreatedAt,
				AppUser::getCreatedBy)
			.containsExactly(KEYCLOAK_ID, AppUserState.PENDING, NOW, "admin");
		verify(audit).recordCreation("app_user", 7L,
				Map.of("fullName", "Ana María Ríos", "email", "ana.rios@example.com", "role", "npd-leader"), "admin");
		assertThat(response).isEqualTo(
				new UserResponse(7L, "Ana María Ríos", "ana.rios@example.com", "npd-leader", "PENDING", null));
	}

	/** An email already in {@code app_user} is rejected before calling Keycloak. */
	@Test
	void rejectsAnEmailAlreadyRegistered() {
		when(users.existsByEmail("ana.rios@example.com")).thenReturn(true);

		assertEmailTaken();
		verifyNoInteractions(keycloak);
	}

	/** An account created directly in Keycloak also takes the email; there is nothing to undo. */
	@Test
	void rejectsAnEmailAlreadyInKeycloak() {
		when(keycloak.createUser(any(), any())).thenThrow(new KeycloakUserExistsException());

		assertEmailTaken();
		verify(keycloak, never()).deleteUser(any());
	}

	/**
	 * If a step after the account creation fails, the account is deleted and that error is reported,
	 * even when the delete fails too.
	 */
	@ParameterizedTest
	@CsvSource({ "role, false", "invitation, false", "database, false", "invitation, true" })
	void deletesTheAccountWhenALaterStepFails(String failingStep, boolean deleteFails) {
		when(keycloak.createUser(any(), any())).thenReturn(KEYCLOAK_ID);
		RuntimeException failure = new IdentityProviderUnavailableException(new RuntimeException("down"));
		if (deleteFails) {
			doThrow(new IllegalStateException("delete")).when(keycloak).deleteUser(KEYCLOAK_ID);
		}
		switch (failingStep) {
			case "role" -> doThrow(failure).when(keycloak).assignRealmRole(KEYCLOAK_ID, Role.NPD_LEADER);
			case "invitation" -> doThrow(failure).when(keycloak).sendInvitation(KEYCLOAK_ID);
			default -> when(users.save(any())).thenThrow(failure);
		}

		assertThatThrownBy(() -> service.register(command)).isSameAs(failure);
		verify(keycloak).deleteUser(KEYCLOAK_ID);
	}

	private void assertEmailTaken() {
		assertThatThrownBy(() -> service.register(command)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo(ErrorCode.EMAIL_TAKEN);
		verify(users, never()).save(any());
	}

}
