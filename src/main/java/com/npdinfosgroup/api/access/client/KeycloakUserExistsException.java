package com.npdinfosgroup.api.access.client;

/**
 * Keycloak already has an account with that username (the email). The service that creates users
 * turns it into the {@code EMAIL_TAKEN} error.
 */
public class KeycloakUserExistsException extends RuntimeException {

	/**
	 * Creates the error. The email is not in the message, so it never reaches the logs.
	 */
	public KeycloakUserExistsException() {
		super("Keycloak already has an account with that username");
	}

}
