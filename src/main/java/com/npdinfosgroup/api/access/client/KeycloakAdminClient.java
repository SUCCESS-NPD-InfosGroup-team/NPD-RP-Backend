package com.npdinfosgroup.api.access.client;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.npdinfosgroup.api.shared.security.Role;

/**
 * The calls to the Keycloak Admin REST API that creating and editing a user need: create the account,
 * give it its realm role, send the invitation email and, to undo a failed creation, delete it
 * ({@code docs/specs/SCRUM-93/design.md} §3); rename it, replace its role and close its sessions
 * ({@code docs/specs/SCRUM-53/design.md} §3); disable, enable and delete it for the deactivation
 * ({@code docs/specs/SCRUM-231/design.md} §4).
 * <p>
 * Errors: a 409 on creation is {@link KeycloakUserExistsException}; Keycloak down, a timeout or a 5xx
 * (also when getting the token) is {@link IdentityProviderUnavailableException}; a 404 on an edit (the
 * account was deleted in the Keycloak console) is an {@link IllegalStateException}. Anything else, such
 * as a rejected secret or missing permissions, is our configuration and propagates as it is.
 */
@Component
public class KeycloakAdminClient {

	private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

	/** Realm roles of the platform; any other realm role of an account is left as it is. */
	private static final Set<String> PLATFORM_ROLES = Arrays.stream(Role.values())
		.map(Role::realmName)
		.collect(Collectors.toUnmodifiableSet());

	private static final ParameterizedTypeReference<Map<String, Object>> REPRESENTATION = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<List<RealmRole>> ROLE_LIST = new ParameterizedTypeReference<>() {
	};

	private final RestClient keycloak;

	private final KeycloakAdminProperties properties;

	/**
	 * Creates the client.
	 *
	 * @param keycloak HTTP client with the service-account token and the admin URL of the realm
	 * @param properties Keycloak settings, for the invitation link
	 */
	KeycloakAdminClient(@Qualifier("keycloakAdminRestClient") RestClient keycloak, KeycloakAdminProperties properties) {
		this.keycloak = keycloak;
		this.properties = properties;
	}

	/**
	 * Creates an enabled account whose username is the email, without a password: the user sets it
	 * from the invitation.
	 *
	 * @param email email and username of the account
	 * @param fullName full name, stored as the first name
	 * @return the Keycloak id of the new account
	 * @throws KeycloakUserExistsException if an account with that username exists
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 */
	public UUID createUser(String email, String fullName) {
		URI location = call("create user", () -> {
			try {
				return keycloak.post()
					.uri("/users")
					.contentType(MediaType.APPLICATION_JSON)
					.body(new NewUser(email, email, fullName, true, false))
					.retrieve()
					.toBodilessEntity()
					.getHeaders()
					.getLocation();
			}
			catch (HttpClientErrorException.Conflict ex) {
				throw new KeycloakUserExistsException();
			}
		});
		if (location == null) {
			throw new IllegalStateException("Keycloak created the user without a Location header");
		}
		String path = location.getPath();
		return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
	}

	/**
	 * Gives the account its realm role.
	 *
	 * @param userId Keycloak id of the account
	 * @param role the role
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 */
	public void assignRealmRole(UUID userId, Role role) {
		call("assign role", () -> addRealmRole(userId, role));
	}

	/**
	 * Changes the first name of the account (calls 1 and 2 of the design). The whole representation is
	 * read and sent back, so no other field or attribute of the account is lost.
	 *
	 * @param userId Keycloak id of the account
	 * @param fullName new full name, stored as the first name
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 * @throws IllegalStateException if the account no longer exists
	 */
	public void rename(UUID userId, String fullName) {
		onAccount(userId, "rename user", () -> updateRepresentation(userId, "firstName", fullName));
	}

	/**
	 * Enables or disables the account (calls 1 and 2 of {@code docs/specs/SCRUM-231/design.md} §4). A
	 * disabled account cannot log in nor use its action tokens (the invitation link), but keeps its
	 * roles, so enabling it again restores it as it was. Its open sessions are not closed: see
	 * {@link #logout(UUID)}. The whole representation is sent back, as on {@link #rename(UUID, String)}.
	 *
	 * @param userId Keycloak id of the account
	 * @param enabled {@code false} to deactivate it, {@code true} to reactivate it
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 * @throws IllegalStateException if the account no longer exists
	 */
	public void setEnabled(UUID userId, boolean enabled) {
		onAccount(userId, enabled ? "enable user" : "disable user",
				() -> updateRepresentation(userId, "enabled", enabled));
	}

	/**
	 * Leaves the account with {@code role} as its only platform role (calls 3 to 5 of the design). Only
	 * the four roles of {@link Role} are removed: {@code default-roles-*}, {@code offline_access} and any
	 * other realm role stay. A role the account already has is neither removed nor added again, so the
	 * call can be repeated, as the compensation of a failed edit does.
	 *
	 * @param userId Keycloak id of the account
	 * @param role the new role
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 * @throws IllegalStateException if the account no longer exists
	 */
	public void replaceRealmRole(UUID userId, Role role) {
		onAccount(userId, "replace role", () -> {
			List<RealmRole> current = keycloak.get().uri("/users/{id}/role-mappings/realm", userId).retrieve().body(ROLE_LIST);
			List<RealmRole> platform = current == null ? List.of()
					: current.stream().filter(mapped -> PLATFORM_ROLES.contains(mapped.name())).toList();
			List<RealmRole> toRemove = platform.stream().filter(mapped -> !mapped.name().equals(role.realmName())).toList();
			if (!toRemove.isEmpty()) {
				keycloak.method(HttpMethod.DELETE)
					.uri("/users/{id}/role-mappings/realm", userId)
					.contentType(MediaType.APPLICATION_JSON)
					.body(toRemove)
					.retrieve()
					.toBodilessEntity();
			}
			// every platform role it had was removed: the new one is not among them
			if (platform.size() == toRemove.size()) {
				addRealmRole(userId, role);
			}
			return null;
		});
	}

	/**
	 * Closes every session of the account (call 6 of the design), so the token introspection rejects its
	 * next request and the user logs in again with the new role.
	 *
	 * @param userId Keycloak id of the account
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 * @throws IllegalStateException if the account no longer exists
	 */
	public void logout(UUID userId) {
		onAccount(userId, "log out user",
				() -> keycloak.post().uri("/users/{id}/logout", userId).retrieve().toBodilessEntity());
	}

	/**
	 * Emails the user a link to set the password. The link is valid for the configured lifespan and
	 * leads back to the frontend's invitation page.
	 *
	 * @param userId Keycloak id of the account
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached or fails to send it
	 */
	public void sendInvitation(UUID userId) {
		call("send invitation", () -> keycloak.put()
			.uri(uri -> uri.path("/users/{id}/execute-actions-email")
				.queryParam("client_id", properties.frontendClientId())
				.queryParam("redirect_uri", properties.invitationRedirectUri())
				.queryParam("lifespan", properties.invitationLifespan().toSeconds())
				.build(userId))
			.contentType(MediaType.APPLICATION_JSON)
			.body(List.of("UPDATE_PASSWORD"))
			.retrieve()
			.toBodilessEntity());
	}

	/**
	 * Deletes the account; used to undo a creation that failed halfway.
	 *
	 * @param userId Keycloak id of the account
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 */
	public void deleteUser(UUID userId) {
		call("delete user", () -> keycloak.delete().uri("/users/{id}", userId).retrieve().toBodilessEntity());
	}

	/**
	 * Deletes the account of a user being deleted, which also ends its sessions
	 * ({@code docs/specs/SCRUM-231/design.md} §2). An account that no longer exists counts as deleted, so a
	 * deletion that failed after this call can simply be repeated.
	 *
	 * @param userId Keycloak id of the account
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached
	 */
	public void deleteAccount(UUID userId) {
		try {
			deleteUser(userId);
		}
		catch (HttpClientErrorException.NotFound ex) {
			log.info("Keycloak account {} was already deleted", userId);
		}
	}

	/** Reads the whole representation of the account and sends it back with one field changed. */
	private Object updateRepresentation(UUID userId, String field, Object value) {
		Map<String, Object> current = keycloak.get().uri("/users/{id}", userId).retrieve().body(REPRESENTATION);
		Map<String, Object> changed = new HashMap<>(current == null ? Map.of() : current);
		changed.put(field, value);
		return keycloak.put()
			.uri("/users/{id}", userId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(changed)
			.retrieve()
			.toBodilessEntity();
	}

	/** Reads the realm role by its name and maps it to the account. */
	private Object addRealmRole(UUID userId, Role role) {
		RealmRole realmRole = keycloak.get().uri("/roles/{role}", role.realmName()).retrieve().body(RealmRole.class);
		return keycloak.post()
			.uri("/users/{id}/role-mappings/realm", userId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(List.of(realmRole))
			.retrieve()
			.toBodilessEntity();
	}

	/**
	 * Runs a call on an account that must exist. A 404 means the account was deleted in the Keycloak
	 * console while it is still in {@code app_user}: an inconsistency to fix by hand, logged with its id.
	 */
	private void onAccount(UUID userId, String operation, Supplier<?> request) {
		try {
			call(operation, request);
		}
		catch (HttpClientErrorException.NotFound ex) {
			log.error("Keycloak account {} not found on {}", userId, operation);
			throw new IllegalStateException("Keycloak account " + userId + " not found", ex);
		}
	}

	/**
	 * Runs a call and turns "Keycloak is not available" failures into
	 * {@link IdentityProviderUnavailableException}. Only the operation and the error type are logged,
	 * never the request, which carries personal data.
	 */
	private <T> T call(String operation, Supplier<T> request) {
		try {
			return request.get();
		}
		catch (HttpServerErrorException | ResourceAccessException ex) {
			throw unavailable(operation, ex);
		}
		catch (OAuth2AuthorizationException ex) {
			// The token request wraps its own HTTP failure: Keycloak down is not a configuration error
			if (causedByOutage(ex)) {
				throw unavailable(operation, ex);
			}
			throw ex;
		}
	}

	private static boolean causedByOutage(Throwable ex) {
		for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof HttpServerErrorException || cause instanceof ResourceAccessException) {
				return true;
			}
		}
		return false;
	}

	private static IdentityProviderUnavailableException unavailable(String operation, Exception ex) {
		log.warn("Keycloak unavailable on {}: {}", operation, ex.getClass().getName());
		return new IdentityProviderUnavailableException(ex);
	}

	record NewUser(String username, String email, String firstName, boolean enabled, boolean emailVerified) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RealmRole(String id, String name) {
	}

}
