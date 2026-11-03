package com.npdinfosgroup.api.access.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

/**
 * Keycloak settings: the token and admin URLs come from the issuer, so the API needs only one Keycloak
 * URL, and the application does not start with invalid settings.
 */
class KeycloakAdminPropertiesTest {

	private final ApplicationContextRunner context = new ApplicationContextRunner()
		.withUserConfiguration(KeycloakAdminClientConfig.class)
		.withPropertyValues("app.keycloak.issuer-uri=https://auth.example.com/realms/npd-rp",
				"app.keycloak.admin-client-id=npd-rp-api-admin", "app.keycloak.connect-timeout=2s",
				"app.keycloak.read-timeout=5s", "app.keycloak.frontend-client-id=npd-rp-web",
				"app.keycloak.invitation-redirect-uri=http://localhost:5173/recursos",
				"app.keycloak.invitation-lifespan=48h");

	/** With or without a trailing slash, the issuer gives the realm's token and admin URLs. */
	@ParameterizedTest
	@ValueSource(strings = { "https://auth.example.com/realms/npd-rp", "https://auth.example.com/realms/npd-rp/" })
	void derivesTheTokenAndAdminUrlsFromTheIssuer(String issuer) {
		KeycloakAdminProperties properties = new KeycloakAdminProperties(issuer, "npd-rp-api-admin", "s3cret",
				Duration.ofSeconds(2), Duration.ofSeconds(5), "npd-rp-web",
				"http://localhost:5173/recursos", Duration.ofHours(48));

		assertThat(properties.tokenUri())
			.isEqualTo(URI.create("https://auth.example.com/realms/npd-rp/protocol/openid-connect/token"));
		assertThat(properties.adminUri()).isEqualTo(URI.create("https://auth.example.com/admin/realms/npd-rp"));
	}

	/** With every setting, the admin client is created. */
	@Test
	void createsTheClientWithValidSettings() {
		context.withPropertyValues("app.keycloak.admin-client-secret=s3cret")
			.run(started -> assertThat(started).hasNotFailed().hasSingleBean(RestClient.class));
	}

	/**
	 * A missing secret or frontend client, an issuer that is not a realm URL, or a redirect without the
	 * frontend URL stops the application from starting.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "app.keycloak.admin-client-secret=",
			"app.keycloak.issuer-uri=https://auth.example.com/npd-rp", "app.keycloak.frontend-client-id=",
			"app.keycloak.invitation-redirect-uri=/recursos" })
	void failsToStartWithInvalidSettings(String invalidSetting) {
		context.withPropertyValues("app.keycloak.admin-client-secret=s3cret", invalidSetting)
			.run(started -> assertThat(started).hasFailed());
	}

}
