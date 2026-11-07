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
 * {@code docs/specs/SCRUM-93/design.md}.
 * <p>
 * The application does not start if one is missing or invalid.
 *
 * @param issuerUri realm that issues the tokens, the same as the resource server's
 *        ({@code {keycloak}/realms/{realm}}); the token and admin URLs come from it
 * @param adminUrl optional Keycloak server URL ({@code {keycloak}}) for the token and admin calls, when
 *        the API cannot reach Keycloak at the issuer's address (inside docker compose,
 *        {@code http://keycloak:8080}); empty or missing to use the issuer's server. The realm always
 *        comes from the issuer
 * @param adminClientId client id of the service account
 * @param adminClientSecret secret of the service account
 * @param connectTimeout how long to wait to connect to Keycloak
 * @param readTimeout how long to wait for each Keycloak answer
 * @param frontendClientId client of the frontend, whose pages the invitation email links to
 * @param invitationRedirectUri frontend page the user goes back to after setting the password
 * @param invitationLifespan how long the invitation link is valid
 */
@Validated
@ConfigurationProperties("app.keycloak")
public record KeycloakAdminProperties(
		@NotNull @Pattern(regexp = "https?://.+/realms/[^/]+/?") String issuerUri,
		@Pattern(regexp = "(https?://.+)?") String adminUrl,
		@NotBlank String adminClientId,
		@NotBlank String adminClientSecret,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout,
		@NotBlank String frontendClientId,
		@NotNull @Pattern(regexp = "https?://.+") String invitationRedirectUri,
		@NotNull Duration invitationLifespan) {

	private static final String REALMS = "/realms/";

	/**
	 * Endpoint where the service account gets its tokens.
	 *
	 * @return {@code {keycloak}/realms/{realm}/protocol/openid-connect/token}
	 */
	URI tokenUri() {
		return URI.create(server() + realmPath() + "/protocol/openid-connect/token");
	}

	/**
	 * Base URL of the Admin REST API for the realm.
	 *
	 * @return {@code {keycloak}/admin/realms/{realm}}
	 */
	URI adminUri() {
		return URI.create(server() + "/admin" + realmPath());
	}

	/** The admin URL when set, otherwise the issuer up to {@code /realms/}; never with a trailing slash. */
	private String server() {
		if (adminUrl == null || adminUrl.isBlank()) {
			String issuer = issuer();
			return issuer.substring(0, issuer.lastIndexOf(REALMS));
		}
		return withoutTrailingSlash(adminUrl);
	}

	/** {@code /realms/{realm}}, taken from the issuer. */
	private String realmPath() {
		String issuer = issuer();
		return issuer.substring(issuer.lastIndexOf(REALMS));
	}

	private String issuer() {
		return withoutTrailingSlash(issuerUri);
	}

	private static String withoutTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
