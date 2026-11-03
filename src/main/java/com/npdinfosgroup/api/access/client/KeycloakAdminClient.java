package com.npdinfosgroup.api.access.client;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * The calls to the Keycloak Admin REST API that creating a user needs: create the account, give it its
 * realm role and, to undo a failed creation, delete it. See
 * {@code docs/specs/SCRUM-93/design.md} §3.
 * <p>
 * Errors: a 409 on creation is {@link KeycloakUserExistsException}; Keycloak down, a timeout or a 5xx
 * (also when getting the token) is {@link IdentityProviderUnavailableException}. Anything else, such
 * as a rejected secret or missing permissions, is our configuration and propagates as it is.
 */
@Component
public class KeycloakAdminClient {

	private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

	private final RestClient keycloak;

	/**
	 * Creates the client.
	 *
	 * @param keycloak HTTP client with the service-account token and the admin URL of the realm
	 */
	KeycloakAdminClient(@Qualifier("keycloakAdminRestClient") RestClient keycloak) {
		this.keycloak = keycloak;
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
		call("assign role", () -> {
			RealmRole realmRole = keycloak.get().uri("/roles/{role}", role.realmName()).retrieve().body(RealmRole.class);
			return keycloak.post()
				.uri("/users/{id}/role-mappings/realm", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(List.of(realmRole))
				.retrieve()
				.toBodilessEntity();
		});
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
