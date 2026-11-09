package com.npdinfosgroup.api.access.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The validator accepts a token only while Keycloak says it is active, and refuses it with a distinct
 * error when Keycloak cannot be asked; a mock server plays the introspection endpoint.
 */
class KeycloakSessionValidatorTest {

	private static final String INTROSPECTION_URL = "http://keycloak.test/realms/npd/protocol/openid-connect/token/introspect";

	private static final KeycloakAdminProperties PROPERTIES = new KeycloakAdminProperties("http://keycloak.test/realms/npd",
			null, "npd-rp-api-admin", "s3cret", Duration.ofSeconds(2), Duration.ofSeconds(5), "npd-rp-web",
			"http://localhost:5173/recursos", Duration.ofHours(48));

	private static final Jwt TOKEN = Jwt.withTokenValue("user-access-token").header("alg", "RS256").subject("user").build();

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final KeycloakSessionValidator validator = new KeycloakSessionValidator(
			KeycloakIntrospectionConfig.create(builder, PROPERTIES));

	/**
	 * The token is sent as a form to the realm's introspection endpoint, with the service account's
	 * credentials, and an active token is accepted.
	 */
	@Test
	void acceptsAnActiveTokenAfterAskingKeycloakAsTheServiceAccount() {
		String credentials = Base64.getEncoder().encodeToString("npd-rp-api-admin:s3cret".getBytes(StandardCharsets.UTF_8));
		server.expect(requestTo(INTROSPECTION_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(AUTHORIZATION, "Basic " + credentials))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().string("token=user-access-token&token_type_hint=access_token"))
			.andRespond(withSuccess("""
					{"active": true, "preferred_username": "mperez"}""", APPLICATION_JSON));

		OAuth2TokenValidatorResult result = validator.validate(TOKEN);

		assertThat(result.hasErrors()).isFalse();
		server.verify();
	}

	/** After logout Keycloak answers only {@code active: false}; a body without it is treated the same. */
	@ParameterizedTest
	@ValueSource(strings = { "{\"active\": false}", "{}" })
	void rejectsATokenWhoseSessionHasEnded(String answer) {
		server.expect(requestTo(INTROSPECTION_URL)).andRespond(withSuccess(answer, APPLICATION_JSON));

		assertThat(errorOf(validator.validate(TOKEN))).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
	}

	/** A Keycloak failure, or our client being rejected, never lets the token through unchecked. */
	@ParameterizedTest
	@ValueSource(ints = { 500, 503, 401 })
	void refusesTheTokenWhenKeycloakAnswersWithAnError(int status) {
		server.expect(requestTo(INTROSPECTION_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

		assertThat(errorOf(validator.validate(TOKEN))).isEqualTo(OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE);
	}

	/** Keycloak unreachable or too slow: the token is refused as not checked. */
	@Test
	void refusesTheTokenWhenKeycloakCannotBeReached() {
		server.expect(requestTo(INTROSPECTION_URL)).andRespond(withException(new IOException("Connection refused")));

		assertThat(errorOf(validator.validate(TOKEN))).isEqualTo(OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE);
	}

	private static String errorOf(OAuth2TokenValidatorResult result) {
		assertThat(result.getErrors()).hasSize(1);
		return result.getErrors().iterator().next().getErrorCode();
	}

}
