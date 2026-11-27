package com.npdinfosgroup.api.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.List;
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
 * Deleting a user through the API (SCRUM-231), end to end with the database; Keycloak is replaced by a
 * mock of its client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class UserDeletionControllerTest {

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
		jdbc.sql("DELETE FROM employee WHERE code = 'UD-1'").update();
		jdbc.sql("DELETE FROM job_position WHERE name = 'UD Posicion'").update();
	}

	/**
	 * CA-01, CA-02, CA-06: the account is deleted from Keycloak, the user leaves the list and the audit
	 * keeps who deleted it and its name, email and role.
	 */
	@Test
	void deletesAUser() {
		User ana = insert("Ana Torres", "customer-success", "ACTIVE");

		delete(ana.id(), "0", ADMIN).expectStatus().isNoContent();

		verify(keycloak).deleteAccount(ana.keycloakId());
		client.get().uri("/api/v1/access/users/" + ana.id()).header(AUTHORIZATION, "Bearer " + ADMIN).exchange()
			.expectStatus().isNotFound();
		assertThat(auditRows()).containsExactly("DELETE fullName: Ana Torres by " + ACTOR,
				"DELETE email: " + ana.email() + " by " + ACTOR, "DELETE role: customer-success by " + ACTOR);
	}

	/** CA-04: the only active Operations administrator cannot be deleted; nothing changes. */
	@Test
	void protectsTheLastOperationsAdministrator() {
		User admin = insert("Jorge Ríos", ADMIN, "ACTIVE");

		delete(admin.id(), "0", ADMIN).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("LAST_OPERATIONS_ADMINISTRATOR");

		verifyNoInteractions(keycloak);
		assertThat(count(admin.id())).isOne();
	}

	/** CA-09: an NPD lead's employee record stays, still an NPD lead. */
	@Test
	void keepsTheEmployeeRecordOfAnNpdLead() {
		jdbc.sql("INSERT INTO job_position (name) VALUES ('UD Posicion')").update();
		long employeeId = jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, hire_date, status_id, is_npd_lead)
				VALUES ('UD-1', 'Lorena Batista', (SELECT id FROM gender WHERE code = 'F'),
				        (SELECT id FROM job_position WHERE name = 'UD Posicion'), DATE '2024-01-15',
				        (SELECT id FROM resource_status WHERE code = 'ACTIVO'), true)
				RETURNING id""").query(Long.class).single();
		User lorena = insert("Lorena Batista", "npd-leader", "ACTIVE");
		jdbc.sql("UPDATE app_user SET employee_id = :employee WHERE id = :id").param("employee", employeeId)
			.param("id", lorena.id()).update();

		delete(lorena.id(), "0", ADMIN).expectStatus().isNoContent();

		assertThat(jdbc.sql("SELECT is_npd_lead FROM employee WHERE id = :id").param("id", employeeId)
			.query(Boolean.class).single()).isTrue();
	}

	/** Keycloak down: 503 and the user stays, with no audit. */
	@Test
	void keepsTheUserWhenKeycloakIsDown() {
		User ana = insert("Ana Torres", "guest", "PENDING");
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.deleteAccount(ana.keycloakId());

		delete(ana.id(), "0", ADMIN).expectStatus().isEqualTo(503);

		assertThat(count(ana.id())).isOne();
		assertThat(auditRows()).isEmpty();
	}

	/** A stale version, a missing one and an unknown user are rejected. */
	@Test
	void checksTheRequest() {
		User ana = insert("Ana Torres", "guest", "ACTIVE");

		delete(ana.id(), "5", ADMIN).expectStatus().isEqualTo(409).expectBody().jsonPath("$.currentVersion")
			.isEqualTo(0);
		client.delete().uri("/api/v1/access/users/" + ana.id()).header(AUTHORIZATION, "Bearer " + ADMIN).exchange()
			.expectStatus().isBadRequest()
			.expectBody()
			.jsonPath("$.errors[0].field").isEqualTo("version");
		delete(ana.id() + 1000, "0", ADMIN).expectStatus().isNotFound();
		verifyNoInteractions(keycloak);
	}

	/** CA-05: only Operations administrators delete; without a token it is refused. */
	@ParameterizedTest
	@ValueSource(strings = { "npd-leader", "customer-success", "guest" })
	void onlyOperationsAdministratorsDelete(String role) {
		User ana = insert("Ana Torres", "guest", "ACTIVE");

		delete(ana.id(), "0", role).expectStatus().isForbidden();
		client.delete().uri("/api/v1/access/users/" + ana.id() + "?version=0").exchange()
			.expectStatus().isUnauthorized();

		verifyNoInteractions(keycloak);
		assertThat(count(ana.id())).isOne();
	}

	private User insert(String fullName, String role, String state) {
		UUID keycloakId = UUID.randomUUID();
		String email = UUID.randomUUID() + "@example.com";
		long id = jdbc.sql("""
				INSERT INTO app_user (keycloak_id, full_name, email, role, state, created_at, created_by)
				VALUES (:keycloakId, :name, :email, :role, :state, now(), 'test')
				RETURNING id""")
			.param("keycloakId", keycloakId)
			.param("name", fullName)
			.param("email", email)
			.param("role", role)
			.param("state", state)
			.query(Long.class)
			.single();
		return new User(id, keycloakId, email);
	}

	private int count(long id) {
		return jdbc.sql("SELECT count(*) FROM app_user WHERE id = :id").param("id", id).query(Integer.class).single();
	}

	private List<String> auditRows() {
		return jdbc.sql("""
				SELECT action || ' ' || field || ': ' || old_value || ' by ' || actor FROM audit_log
				WHERE entity_type = 'app_user' AND new_value IS NULL ORDER BY id""")
			.query(String.class)
			.list();
	}

	private RestTestClient.ResponseSpec delete(long id, String version, String role) {
		return client.delete()
			.uri("/api/v1/access/users/" + id + "?version=" + version)
			.header(AUTHORIZATION, "Bearer " + role)
			.exchange();
	}

	private record User(long id, UUID keycloakId, String email) {
	}

}
