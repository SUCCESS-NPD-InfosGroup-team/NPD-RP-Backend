package com.npdinfosgroup.api.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Deactivating a user account through the API (SCRUM-231, first version, being removed), end to end with the
 * database; Keycloak is replaced by a mock of its client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class UserActivationControllerTest {

	private static final String ADMIN = "operations-administrator";

	private static final String ACTOR = TestSecurityConfiguration.usernameOf(ADMIN);

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
		jdbc.sql("DELETE FROM employee WHERE code = 'UA-1'").update();
		jdbc.sql("DELETE FROM job_position WHERE name = 'UA Posicion'").update();
	}

	/**
	 * The account is disabled with its sessions closed, its deletion time is one day later, and the
	 * change is audited.
	 */
	@Test
	void deactivatesAnAccount() {
		User ana = insert("Ana Torres", "customer-success", "ACTIVE");
		Instant before = Instant.now();

		post(ana, "deactivation", 0).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.state").isEqualTo("DISABLED")
			.jsonPath("$.version").isEqualTo(1)
			.jsonPath("$.deletesAt").isNotEmpty();
		verify(keycloak).setEnabled(ana.keycloakId(), false);
		verify(keycloak).logout(ana.keycloakId());
		Instant saved = jdbc.sql("SELECT disabled_at FROM app_user WHERE id = :id").param("id", ana.id())
			.query(Instant.class).single();
		assertThat(saved).isBetween(before.minusSeconds(1), Instant.now());
		client.get().uri("/api/v1/access/users/" + ana.id()).header(AUTHORIZATION, "Bearer " + ADMIN).exchange()
			.expectBody()
			.jsonPath("$.disabledAt").isEqualTo(saved.toString())
			.jsonPath("$.deletesAt").isEqualTo(saved.plus(Duration.ofDays(1)).toString());
		assertThat(auditRows()).containsExactly("state: ACTIVE -> DISABLED by " + ACTOR);
	}

	/** A deactivated account cannot be edited until it is reactivated; nothing changes. */
	@Test
	void rejectsTheEditOfADeactivatedAccount() {
		User ana = insert("Ana Torres", "guest", "DISABLED");

		client.put().uri("/api/v1/access/users/" + ana.id()).header(AUTHORIZATION, "Bearer " + ADMIN)
			.contentType(APPLICATION_JSON).body(Map.of("version", 0, "fullName", "Ana", "role", "guest")).exchange()
			.expectStatus().isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("ACCOUNT_DISABLED");

		verifyNoInteractions(keycloak);
		assertThat(read(ana.id(), "full_name")).isEqualTo("Ana Torres");
	}

	/** Repeating a deactivation answers the user as it is, with no call and no audit. */
	@Test
	void deactivatingTwiceChangesNothing() {
		User ana = insert("Ana Torres", "guest", "ACTIVE");
		post(ana, "deactivation", 0).expectStatus().isOk();

		post(ana, "deactivation", 1).expectStatus().isOk().expectBody().jsonPath("$.version").isEqualTo(1);

		verify(keycloak).setEnabled(ana.keycloakId(), false);
		assertThat(auditRows()).hasSize(1);
	}

	/** CA-04: the only active Operations administrator cannot be deactivated; nothing changes. */
	@Test
	void protectsTheLastOperationsAdministrator() {
		User admin = insert("Jorge Ríos", ADMIN, "ACTIVE");

		post(admin, "deactivation", 0).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("LAST_OPERATIONS_ADMINISTRATOR");

		verifyNoInteractions(keycloak);
		assertThat(read(admin.id(), "state")).isEqualTo("ACTIVE");
	}

	/** CA-09: an NPD lead's employee record is not changed: it stays Activo and can still be chosen. */
	@Test
	void keepsTheEmployeeRecordOfAnNpdLead() {
		jdbc.sql("INSERT INTO job_position (name) VALUES ('UA Posicion')").update();
		long employeeId = jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, hire_date, status_id, is_npd_lead)
				VALUES ('UA-1', 'Lorena Batista', (SELECT id FROM gender WHERE code = 'F'),
				        (SELECT id FROM job_position WHERE name = 'UA Posicion'), DATE '2024-01-15',
				        (SELECT id FROM resource_status WHERE code = 'ACTIVO'), true)
				RETURNING id""").query(Long.class).single();
		User lorena = insert("Lorena Batista", "npd-leader", "ACTIVE");
		jdbc.sql("UPDATE app_user SET employee_id = :employee WHERE id = :id").param("employee", employeeId)
			.param("id", lorena.id()).update();

		post(lorena, "deactivation", 0).expectStatus().isOk().expectBody().jsonPath("$.employee.status")
			.isEqualTo("ACTIVO");

		assertThat(jdbc.sql("SELECT is_npd_lead FROM employee WHERE id = :id").param("id", employeeId)
			.query(Boolean.class).single()).isTrue();
	}

	/** Keycloak down: 503 and the row is left as it was. */
	@Test
	void leavesTheUserUnchangedWhenKeycloakIsDown() {
		User ana = insert("Ana Torres", "guest", "ACTIVE");
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.setEnabled(ana.keycloakId(), false);

		post(ana, "deactivation", 0).expectStatus().isEqualTo(503);

		assertThat(read(ana.id(), "state")).isEqualTo("ACTIVE");
		assertThat(auditRows()).isEmpty();
	}

	/** A stale version and a missing one are rejected. */
	@Test
	void checksTheVersion() {
		User ana = insert("Ana Torres", "guest", "ACTIVE");

		post(ana, "deactivation", 5).expectStatus().isEqualTo(409).expectBody().jsonPath("$.currentVersion")
			.isEqualTo(0);
		client.post().uri("/api/v1/access/users/" + ana.id() + "/deactivation").header(AUTHORIZATION, "Bearer " + ADMIN)
			.contentType(APPLICATION_JSON).body(Map.of()).exchange()
			.expectStatus().isBadRequest();
		verifyNoInteractions(keycloak);
	}

	/** Only Operations administrators deactivate; without a token it is refused. */
	@ParameterizedTest
	@ValueSource(strings = { "npd-leader", "customer-success", "guest" })
	void onlyOperationsAdministratorsChangeTheState(String role) {
		User ana = insert("Ana Torres", "guest", "ACTIVE");

		client.post().uri("/api/v1/access/users/" + ana.id() + "/deactivation").header(AUTHORIZATION, "Bearer " + role)
			.contentType(APPLICATION_JSON).body(Map.of("version", 0)).exchange()
			.expectStatus().isForbidden();
		client.post().uri("/api/v1/access/users/" + ana.id() + "/deactivation")
			.contentType(APPLICATION_JSON).body(Map.of("version", 0)).exchange()
			.expectStatus().isUnauthorized();
		verifyNoInteractions(keycloak);
		assertThat(read(ana.id(), "state")).isEqualTo("ACTIVE");
	}

	private User insert(String fullName, String role, String state) {
		UUID keycloakId = UUID.randomUUID();
		long id = jdbc.sql("""
				INSERT INTO app_user (keycloak_id, full_name, email, role, state, created_at, created_by, disabled_at,
				                      last_access_at)
				VALUES (:keycloakId, :name, :email, :role, :state, now(), 'test',
				        CASE WHEN :state = 'DISABLED' THEN now() END, CASE WHEN :state <> 'PENDING' THEN now() END)
				RETURNING id""")
			.param("keycloakId", keycloakId)
			.param("name", fullName)
			.param("email", UUID.randomUUID() + "@example.com")
			.param("role", role)
			.param("state", state)
			.query(Long.class)
			.single();
		return new User(id, keycloakId);
	}

	private String read(long id, String column) {
		return jdbc.sql("SELECT " + column + " FROM app_user WHERE id = :id").param("id", id).query(String.class).single();
	}

	private List<String> auditRows() {
		return jdbc.sql("""
				SELECT field || ': ' || old_value || ' -> ' || new_value || ' by ' || actor FROM audit_log
				WHERE entity_type = 'app_user' ORDER BY id""")
			.query(String.class)
			.list();
	}

	private RestTestClient.ResponseSpec post(User user, String action, int version) {
		return client.post()
			.uri("/api/v1/access/users/" + user.id() + "/" + action)
			.header(AUTHORIZATION, "Bearer " + ADMIN)
			.contentType(APPLICATION_JSON)
			.body(Map.of("version", version))
			.exchange();
	}

	private record User(long id, UUID keycloakId) {
	}

}
