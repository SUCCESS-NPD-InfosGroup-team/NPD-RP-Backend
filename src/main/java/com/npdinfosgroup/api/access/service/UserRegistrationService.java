package com.npdinfosgroup.api.access.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Creates user accounts with their role (HU-02): the account in Keycloak, then the {@code app_user}
 * row and its audit. See {@code docs/specs/SCRUM-93/design.md} §3, "Order and consistency".
 * <p>
 * Keycloak and the database cannot share a transaction, so only the insert and the audit run in one,
 * and none stays open while waiting for Keycloak. If anything fails after the account is created, the
 * account is deleted so the administrator can retry.
 */
@Service
public class UserRegistrationService {

	private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);

	private final AppUserRepository users;

	private final KeycloakAdminClient keycloak;

	private final AuditService audit;

	private final TransactionOperations transactions;

	private final Clock clock;

	/**
	 * Creates the service.
	 *
	 * @param users saves the user
	 * @param keycloak creates, sets up and, on failure, deletes the account
	 * @param audit records the initial values
	 * @param transactions runs the insert and the audit in one transaction
	 * @param clock gives the creation moment
	 */
	UserRegistrationService(AppUserRepository users, KeycloakAdminClient keycloak, AuditService audit,
			TransactionOperations transactions, Clock clock) {
		this.users = users;
		this.keycloak = keycloak;
		this.audit = audit;
		this.transactions = transactions;
		this.clock = clock;
	}

	/**
	 * Creates the account with its role, sends the invitation email and saves the user as pending.
	 *
	 * @param command the user and who creates it; its fields are already validated
	 * @return the saved user
	 * @throws ApiException {@link ErrorCode#EMAIL_TAKEN} if a user with that email exists, here or in
	 *         Keycloak; nothing is created
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached; nothing is left created
	 */
	public UserResponse register(CreateUserCommand command) {
		String email = command.email().strip().toLowerCase(Locale.ROOT);
		String fullName = command.fullName().strip();
		if (users.existsByEmail(email)) {
			throw emailTaken(email);
		}
		UUID keycloakId;
		try {
			keycloakId = keycloak.createUser(email, fullName);
		}
		catch (KeycloakUserExistsException ex) {
			// An account created directly in Keycloak, outside the platform
			throw emailTaken(email);
		}
		try {
			keycloak.assignRealmRole(keycloakId, command.role());
			keycloak.sendInvitation(keycloakId);
			AppUser user = transactions.execute(status -> save(keycloakId, fullName, email, command));
			log.info("User {} created", user.getId());
			return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
					user.getState().name(), user.getLastAccessAt());
		}
		catch (RuntimeException ex) {
			deleteAccount(keycloakId);
			throw ex;
		}
	}

	private AppUser save(UUID keycloakId, String fullName, String email, CreateUserCommand command) {
		AppUser user = new AppUser();
		user.setKeycloakId(keycloakId);
		user.setFullName(fullName);
		user.setEmail(email);
		user.setRole(command.role().realmName());
		user.setState(AppUserState.PENDING);
		user.setCreatedAt(clock.instant());
		user.setCreatedBy(command.actor());
		users.save(user);
		Map<String, String> values = new LinkedHashMap<>();
		values.put("fullName", fullName);
		values.put("email", email);
		values.put("role", user.getRole());
		audit.recordCreation("app_user", user.getId(), values, command.actor());
		return user;
	}

	/**
	 * Undoes a failed creation, best effort: if the delete fails, the orphan account is logged to delete it by hand.
	 */
	private void deleteAccount(UUID keycloakId) {
		try {
			keycloak.deleteUser(keycloakId);
		}
		catch (RuntimeException ex) {
			log.error("Could not delete Keycloak account {} after a failed creation: {}", keycloakId,
					ex.getClass().getName());
		}
	}

	private static ApiException emailTaken(String email) {
		return new ApiException(ErrorCode.EMAIL_TAKEN, List.of(email), Map.of());
	}

}
