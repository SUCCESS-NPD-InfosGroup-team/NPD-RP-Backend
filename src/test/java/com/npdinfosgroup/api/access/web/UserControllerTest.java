package com.npdinfosgroup.api.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.shared.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Creating a user through the API (HU-02), end to end with the database; Keycloak is replaced by a mock
 * of its client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class UserControllerTest {

	private static final String ADMIN = "operations-administrator";

	private static final String USERS = "/api/v1/access/users";

	private static final UUID KEYCLOAK_ID = UUID.fromString("5f0c2b8e-1d2a-4c3b-9e4f-6a7b8c9d0e1f");

	private static final Map<String, Object> ANA = Map.of("fullName", " Ana María Ríos ", "email",
			"Ana.Rios@Example.com", "role", "npd-leader");

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private KeycloakAdminClient keycloak;

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log WHERE entity_type = 'app_user'").update();
		jdbc.sql("DELETE FROM app_user").update();
	}

	/**
	 * The account is created with its role and invitation, the user is saved as pending at its location,
	 * and the creation is audited with the administrator's username.
	 */
	@Test
	void createsTheUser() {
		when(keycloak.createUser("ana.rios@example.com", "Ana María Ríos")).thenReturn(KEYCLOAK_ID);

		URI location = post(ADMIN, ANA).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.fullName").isEqualTo("Ana María Ríos")
			.jsonPath("$.email").isEqualTo("ana.rios@example.com")
			.jsonPath("$.role").isEqualTo("npd-leader")
			.jsonPath("$.state").isEqualTo("PENDING")
			.jsonPath("$.lastAccessAt").doesNotExist()
			.returnResult()
			.getResponseHeaders()
			.getLocation();

		long id = jdbc.sql("SELECT id FROM app_user WHERE keycloak_id = :id").param("id", KEYCLOAK_ID)
			.query(Long.class)
			.single();
		assertThat(location).hasPath(USERS + "/" + id);
		get(location.getPath()).expectStatus().isOk().expectBody().jsonPath("$.email").isEqualTo("ana.rios@example.com");
		verify(keycloak).assignRealmRole(KEYCLOAK_ID, Role.NPD_LEADER);
		verify(keycloak).sendInvitation(KEYCLOAK_ID);
		assertThat(jdbc.sql("SELECT field || '=' || new_value || ' by ' || actor FROM audit_log WHERE entity_type = 'app_user'")
			.query(String.class)
			.list()).containsExactlyInAnyOrder("fullName=Ana María Ríos by operations-administrator.user",
					"email=ana.rios@example.com by operations-administrator.user",
					"role=npd-leader by operations-administrator.user");
	}

	/** Every invalid field is reported at once, in Spanish, and Keycloak is not called. */
	@Test
	void reportsEveryInvalidField() {
		post(ADMIN, Map.of("fullName", " ", "email", "ana@local", "role", "admin")).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
			.jsonPath("$.errors[*].field").value(List.class,
					fields -> assertThat(fields).containsExactlyInAnyOrder("fullName", "email", "role"))
			.jsonPath("$.errors[?(@.field == 'email')].message").isEqualTo(List.of("Escribe un correo válido."))
			.jsonPath("$.errors[?(@.field == 'role')].code").isEqualTo(List.of("UNKNOWN_VALUE"));
		post(ADMIN, Map.of("fullName", "Ana", "email", "ana@example.com")).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[*].field").isEqualTo(List.of("role"))
			.jsonPath("$.errors[0].message").isEqualTo("Selecciona un rol.");
		verifyNoInteractions(keycloak);
	}

	/** An email that already has a user is rejected before calling Keycloak. */
	@Test
	void rejectsATakenEmail() {
		insertUser("Ana", "ana.rios@example.com", "ACTIVE", "now()");

		post(ADMIN, ANA).expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("EMAIL_TAKEN");
		verifyNoInteractions(keycloak);
	}

	/** Keycloak down is a 503 that leaves nothing saved, so the administrator can retry. */
	@Test
	void reportsKeycloakUnavailable() {
		when(keycloak.createUser(any(), any())).thenThrow(new IdentityProviderUnavailableException(new RuntimeException()));

		post(ADMIN, ANA).expectStatus()
			.isEqualTo(503)
			.expectBody()
			.jsonPath("$.code").isEqualTo("IDENTITY_PROVIDER_UNAVAILABLE");
		assertThat(jdbc.sql("SELECT count(*) FROM app_user").query(Long.class).single()).isZero();
	}

	/** The list goes by full name, page by page, with the state and the last access. */
	@Test
	void listsTheUsersByName() {
		insertUser("Carlos Pérez", "carlos@example.com", "ACTIVE", "now()");
		insertUser("Ana Ríos", "ana@example.com", "PENDING", "NULL");
		insertUser("Beatriz Gómez", "beatriz@example.com", "ACTIVE", "now()");

		get(USERS + "?size=2&sort=email,desc").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].fullName").isEqualTo(List.of("Ana Ríos", "Beatriz Gómez"))
			.jsonPath("$.items[0].state").isEqualTo("PENDING")
			.jsonPath("$.items[0].lastAccessAt").doesNotExist()
			.jsonPath("$.items[1].lastAccessAt").isNotEmpty()
			.jsonPath("$.page.totalElements").isEqualTo(3)
			.jsonPath("$.page.totalPages").isEqualTo(2);
		get(USERS + "?page=1&size=2").expectStatus().isOk().expectBody()
			.jsonPath("$.items[*].fullName").isEqualTo(List.of("Carlos Pérez"));
	}

	/** A user that does not exist is a 404. */
	@Test
	void reportsAMissingUser() {
		get(USERS + "/999999").expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
	}

	/** Without a token the request is refused, and with another role it is forbidden. */
	@Test
	void onlyOperationsAdministratorsCreateUsers() {
		client.post().uri(USERS).contentType(APPLICATION_JSON).body(ANA).exchange().expectStatus().isUnauthorized();
		post("npd-leader", ANA).expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
		client.get().uri(USERS).header(AUTHORIZATION, "Bearer guest").exchange().expectStatus().isForbidden();
		verifyNoInteractions(keycloak);
	}

	private void insertUser(String fullName, String email, String state, String lastAccess) {
		jdbc.sql("""
				INSERT INTO app_user (keycloak_id, full_name, email, role, state, last_access_at, created_at, created_by)
				VALUES (gen_random_uuid(), :name, :email, 'guest', :state, %s, now(), 'test')""".formatted(lastAccess))
			.param("name", fullName)
			.param("email", email)
			.param("state", state)
			.update();
	}

	private RestTestClient.ResponseSpec get(String uri) {
		return client.get().uri(uri).header(AUTHORIZATION, "Bearer " + ADMIN).exchange();
	}

	private RestTestClient.ResponseSpec post(String token, Object body) {
		return client.post()
			.uri(USERS)
			.header(AUTHORIZATION, "Bearer " + token)
			.contentType(APPLICATION_JSON)
			.body(body)
			.exchange();
	}

}
