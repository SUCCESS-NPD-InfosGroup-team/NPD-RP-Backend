package com.npdinfosgroup.api.access.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The Keycloak admin client gets a service-account token and sends it on every call, without a real
 * Keycloak: a mock server answers both the token endpoint and the admin calls.
 */
class KeycloakAdminClientConfigTest {

	private static final String TOKEN_URL = "http://keycloak.test/realms/npd-rp/protocol/openid-connect/token";

	private static final String USERS_COUNT_URL = "http://keycloak.test/admin/realms/npd-rp/users/count";

	private static final KeycloakAdminProperties PROPERTIES = new KeycloakAdminProperties(
			"http://keycloak.test/realms/npd-rp", null, "npd-rp-api-admin", "s3cret", Duration.ofSeconds(2),
			Duration.ofSeconds(5), "npd-rp-web", "http://localhost:5173/recursos", Duration.ofHours(48));

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final RestClient keycloak = KeycloakAdminClientConfig.create(builder, PROPERTIES);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	/**
	 * The token is asked for once, with the client's credentials, and reused even when a different user
	 * makes each request: it belongs to the API, not to them.
	 */
	@Test
	void getsTheServiceAccountTokenOnceAndSendsItOnEveryCall() {
		String credentials = Base64.getEncoder().encodeToString("npd-rp-api-admin:s3cret".getBytes(StandardCharsets.UTF_8));
		server.expect(once(), requestTo(TOKEN_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(AUTHORIZATION, "Basic " + credentials))
			.andExpect(content().string(containsString("grant_type=client_credentials")))
			.andRespond(withSuccess("""
					{"access_token": "service-token", "token_type": "Bearer", "expires_in": 300}""", APPLICATION_JSON));
		server.expect(times(2), requestTo(USERS_COUNT_URL))
			.andExpect(header(AUTHORIZATION, "Bearer service-token"))
			.andRespond(withSuccess("7", APPLICATION_JSON));

		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("mperez", null));
		Integer first = keycloak.get().uri("/users/count").retrieve().body(Integer.class);
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("lgomez", null));
		Integer second = keycloak.get().uri("/users/count").retrieve().body(Integer.class);

		assertThat(first).isEqualTo(7);
		assertThat(second).isEqualTo(7);
		server.verify();
	}

	/** If Keycloak rejects the client's credentials, the admin call is never sent. */
	@Test
	void failsWithoutCallingKeycloakWhenTheCredentialsAreRejected() {
		server.expect(once(), requestTo(TOKEN_URL))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(APPLICATION_JSON).body("""
					{"error": "invalid_client", "error_description": "Invalid client credentials"}"""));

		assertThatThrownBy(() -> keycloak.get().uri("/users/count").retrieve().toBodilessEntity())
			.isInstanceOf(OAuth2AuthorizationException.class)
			.hasMessageContaining("invalid_client");
		server.verify();
	}

}
