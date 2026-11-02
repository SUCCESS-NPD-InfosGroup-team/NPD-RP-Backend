package com.npdinfosgroup.api.access.client;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings to call the Keycloak Admin REST API ({@code app.keycloak.*}). The API calls it as its own
 * service account, a confidential client with the {@code client_credentials} grant; see
 * {@code docs/access/keycloak-admin-api.md}.
 * <p>
 * The application does not start if one is missing or invalid.
 *
 * @param issuerUri realm that issues the tokens, the same as the resource server's
 *        ({@code {keycloak}/realms/{realm}}); the token and admin URLs come from it
 * @param adminClientId client id of the service account
 * @param adminClientSecret secret of the service account
 * @param connectTimeout how long to wait to connect to Keycloak
 * @param readTimeout how long to wait for each Keycloak answer
 */
@Validated
@ConfigurationProperties("app.keycloak")
public record KeycloakAdminProperties(
		@NotNull @Pattern(regexp = "https?://.+/realms/[^/]+/?") String issuerUri,
		@NotBlank String adminClientId,
		@NotBlank String adminClientSecret,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout) {

	private static final String REALMS = "/realms/";

	/**
	 * Endpoint where the service account gets its tokens.
	 *
	 * @return {@code {keycloak}/realms/{realm}/protocol/openid-connect/token}
	 */
	URI tokenUri() {
		return URI.create(issuer() + "/protocol/openid-connect/token");
	}

	/**
	 * Base URL of the Admin REST API for the realm.
	 *
	 * @return {@code {keycloak}/admin/realms/{realm}}
	 */
	URI adminUri() {
		String issuer = issuer();
		int realms = issuer.lastIndexOf(REALMS);
		return URI.create(issuer.substring(0, realms) + "/admin" + issuer.substring(realms));
	}

	private String issuer() {
		return issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri;
	}

}
