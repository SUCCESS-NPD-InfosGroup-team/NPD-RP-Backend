package com.npdinfosgroup.api.access.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.npdinfosgroup.api.shared.security.Role;

/**
 * Each Keycloak admin call sends what Keycloak expects and maps its errors, against a mock server that
 * answers the token request and the admin calls.
 */
class KeycloakAdminClientTest {

	private static final String TOKEN_URL = "http://keycloak.test/realms/npd-rp/protocol/openid-connect/token";

	private static final String USERS_URL = "http://keycloak.test/admin/realms/npd-rp/users";

	private static final UUID USER_ID = UUID.fromString("5f0c2b8e-1d2a-4c3b-9e4f-6a7b8c9d0e1f");

	private static final KeycloakAdminProperties PROPERTIES = new KeycloakAdminProperties(
			"http://keycloak.test/realms/npd-rp", "npd-rp-api-admin", "s3cret", Duration.ofSeconds(2),
			Duration.ofSeconds(5));

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final KeycloakAdminClient keycloak = new KeycloakAdminClient(KeycloakAdminClientConfig.create(builder, PROPERTIES));

	/** The account is created with the email as username, and its id comes from the Location header. */
	@Test
	void createsTheUserAndReturnsItsId() {
		expectToken();
		server.expect(requestTo(USERS_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(AUTHORIZATION, "Bearer service-token"))
			.andExpect(content().json("""
					{"username": "ana@example.com", "email": "ana@example.com", "firstName": "Ana María Ríos",
					 "enabled": true, "emailVerified": false}""", true))
			.andRespond(withCreatedEntity(URI.create(USERS_URL + "/" + USER_ID)));

		assertThat(keycloak.createUser("ana@example.com", "Ana María Ríos")).isEqualTo(USER_ID);
		server.verify();
	}

	/** A 409 means the username (the email) is taken. */
	@Test
	void reportsAnExistingUser() {
		expectToken();
		server.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatThrownBy(() -> keycloak.createUser("ana@example.com", "Ana"))
			.isInstanceOf(KeycloakUserExistsException.class);
	}

	/** A creation answered without Location breaks Keycloak's contract: it is not a user error. */
	@Test
	void failsWhenTheCreatedUserHasNoLocation() {
		expectToken();
		server.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.CREATED));

		assertThatThrownBy(() -> keycloak.createUser("ana@example.com", "Ana")).isInstanceOf(IllegalStateException.class);
	}

	/** The role is read by its realm name and sent back as the account's realm mapping. */
	@Test
	void assignsTheRealmRole() {
		expectToken();
		server.expect(requestTo("http://keycloak.test/admin/realms/npd-rp/roles/npd-leader"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
					{"id": "r-1", "name": "npd-leader", "composite": false, "clientRole": false}""", APPLICATION_JSON));
		server.expect(requestTo(USERS_URL + "/" + USER_ID + "/role-mappings/realm"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
					[{"id": "r-1", "name": "npd-leader"}]""", true))
			.andRespond(withNoContent());

		keycloak.assignRealmRole(USER_ID, Role.NPD_LEADER);
		server.verify();
	}

	/** The account of a failed creation is deleted by its id. */
	@Test
	void deletesTheUser() {
		expectToken();
		server.expect(requestTo(USERS_URL + "/" + USER_ID)).andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());

		keycloak.deleteUser(USER_ID);
		server.verify();
	}

	/** Keycloak failing (5xx) or not answering, on the token request or on the call itself, is an outage. */
	@ParameterizedTest
	@CsvSource({ "true, false", "true, true", "false, false", "false, true" })
	void reportsKeycloakUnavailable(boolean onTokenRequest, boolean timeout) {
		if (!onTokenRequest) {
			expectToken();
		}
		server.expect(requestTo(onTokenRequest ? TOKEN_URL : USERS_URL + "/" + USER_ID))
			.andRespond(timeout ? withException(new SocketTimeoutException()) : withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> keycloak.deleteUser(USER_ID)).isInstanceOf(IdentityProviderUnavailableException.class);
	}

	/** A rejected secret is our configuration, not an outage: it propagates as it is (500). */
	@Test
	void propagatesRejectedCredentials() {
		server.expect(requestTo(TOKEN_URL))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(APPLICATION_JSON).body("""
					{"error": "invalid_client"}"""));

		assertThatThrownBy(() -> keycloak.deleteUser(USER_ID))
			.isInstanceOf(OAuth2AuthorizationException.class)
			.isNotInstanceOf(IdentityProviderUnavailableException.class);
	}

	/** Missing permissions of the service account are our configuration too: the 403 propagates. */
	@Test
	void propagatesMissingPermissions() {
		expectToken();
		server.expect(requestTo(USERS_URL + "/" + USER_ID)).andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThatThrownBy(() -> keycloak.deleteUser(USER_ID)).isInstanceOf(HttpClientErrorException.Forbidden.class);
	}

	private void expectToken() {
		server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess("""
				{"access_token": "service-token", "token_type": "Bearer", "expires_in": 300}""", APPLICATION_JSON));
	}

}
