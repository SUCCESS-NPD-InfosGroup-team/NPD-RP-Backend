package com.npdinfosgroup.api.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.sql.Types;
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
 * Editing the name and the role of a user through the API (SCRUM-53), end to end with the database;
 * Keycloak is replaced by a mock of its client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class UserUpdateControllerTest {

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
		jdbc.sql("DELETE FROM audit_log WHERE entity_type IN ('app_user', 'employee')").update();
		jdbc.sql("DELETE FROM app_user").update();
		jdbc.sql("UPDATE employee SET leader_id = NULL WHERE code LIKE 'UU-%'").update();
		jdbc.sql("DELETE FROM employee WHERE code LIKE 'UU-%'").update();
		jdbc.sql("DELETE FROM job_position WHERE name = 'UU Posicion'").update();
	}

	/** CA-10, CA-11, HU-11 CA-04: new record, renamed with the user, Baja with collaborators, back to Activo. */
	@Test
	void changesToAndFromNpdLead() {
		long positionId = insertPosition();
		User ana = insert("Ana Ríos", "customer-success", "ACTIVE");

		put(ana.id(), Map.of("version", 0, "fullName", "Ana Ríos", "role", "npd-leader", "employee",
				employee("UU-1", positionId)))
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.role").isEqualTo("npd-leader")
			.jsonPath("$.employee.employeeNumber").isEqualTo("UU-1")
			.jsonPath("$.employee.status").isEqualTo("ACTIVO")
			.jsonPath("$.employee.collaborators").isEqualTo(0);
		long employeeId = jdbc.sql("SELECT employee_id FROM app_user WHERE id = :id").param("id", ana.id())
			.query(Long.class)
			.single();
		assertThat(jdbc.sql("SELECT full_name || ' ' || email || ' ' || is_npd_lead FROM employee WHERE id = :id")
			.param("id", employeeId).query(String.class).single()).isEqualTo("Ana Ríos ana.rios@example.com true");

		put(ana.id(), Map.of("version", 1, "fullName", "Ana María Ríos", "role", "npd-leader")).expectStatus().isOk();
		assertThat(jdbc.sql("SELECT full_name FROM employee WHERE id = :id").param("id", employeeId)
			.query(String.class).single()).isEqualTo("Ana María Ríos");

		insertEmployee("UU-2", positionId, employeeId);
		put(ana.id(), Map.of("version", 2, "fullName", "Ana María Ríos", "role", "guest")).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.employee.id").isEqualTo(Math.toIntExact(employeeId))
			.jsonPath("$.employee.status").isEqualTo("BAJA")
			.jsonPath("$.employee.collaborators").isEqualTo(1);

		put(ana.id(), Map.of("version", 3, "fullName", "Ana María Ríos", "role", "npd-leader")).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.employee.status").isEqualTo("ACTIVO");
		assertThat(jdbc.sql("SELECT count(*) FROM employee WHERE email = 'ana.rios@example.com'").query(Long.class)
			.single()).isEqualTo(1);
	}

	/** CA-10: employee errors come with the user's; Keycloak failing leaves no employee record. */
	@Test
	void savesTheEmployeeRecordOnlyWithTheRole() {
		long positionId = insertPosition();
		insertEmployee("UU-9", positionId, null);
		User ana = insert("Ana Ríos", "guest", "ACTIVE");

		put(ana.id(), Map.of("version", 0, "fullName", " ", "role", "npd-leader", "employee",
				employee("UU-9", positionId)))
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[*].field").value(List.class,
					fields -> assertThat(fields).containsExactlyInAnyOrder("fullName", "employee.employeeNumber"))
			.jsonPath("$.errors[?(@.field == 'employee.employeeNumber')].code").isEqualTo(List.of("EMPLOYEE_NUMBER_TAKEN"));

		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.replaceRealmRole(any(), any());
		put(ana.id(), Map.of("version", 0, "fullName", "Ana Ríos", "role", "npd-leader", "employee",
				employee("UU-1", positionId)))
			.expectStatus()
			.isEqualTo(503);
		assertThat(jdbc.sql("SELECT count(*) FROM employee WHERE code = 'UU-1'").query(Long.class).single()).isZero();
		assertThat(role(ana.id())).isEqualTo("guest");
	}

	private Map<String, Object> employee(String number, long positionId) {
		return Map.of("employeeNumber", number, "genderId", catalogId("gender", "F"), "positionId", positionId,
				"workModeId", catalogId("work_modality", "VIRTUAL"), "hireDate", "2026-03-03", "status", "ACTIVO");
	}

	private long insertPosition() {
		return jdbc.sql("INSERT INTO job_position (name) VALUES ('UU Posicion') RETURNING id").query(Long.class).single();
	}

	private void insertEmployee(String code, long positionId, Long leaderId) {
		jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, hire_date, status_id, leader_id)
				VALUES (:code, :code, (SELECT id FROM gender WHERE code = 'M'), :position, DATE '2024-01-15',
				        (SELECT id FROM resource_status WHERE code = 'ACTIVO'), :lead)""")
			.param("code", code)
			.param("position", positionId)
			.param("lead", leaderId, Types.BIGINT)
			.update();
	}

	private short catalogId(String table, String code) {
		return jdbc.sql("SELECT id FROM " + table + " WHERE code = :code").param("code", code).query(Short.class).single();
	}

	/**
	 * CA-01, CA-07: the role is replaced in Keycloak and the sessions are closed; the row gets the new
	 * name and role with a new version, and each changed field is audited.
	 */
	@Test
	void changesTheNameAndTheRole() {
		User ana = insert("Ana Ríos", "guest", "ACTIVE");

		put(ana.id(), Map.of("version", 0, "fullName", " Ana María Ríos ", "role", "customer-success")).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.fullName").isEqualTo("Ana María Ríos")
			.jsonPath("$.role").isEqualTo("customer-success")
			.jsonPath("$.version").isEqualTo(1);

		verify(keycloak).rename(ana.keycloakId(), "Ana María Ríos");
		verify(keycloak).replaceRealmRole(ana.keycloakId(), Role.CUSTOMER_SUCCESS);
		verify(keycloak).logout(ana.keycloakId());
		assertThat(auditRows()).containsExactlyInAnyOrder("fullName: Ana Ríos -> Ana María Ríos by " + ACTOR,
				"role: guest -> customer-success by " + ACTOR);
	}

	/** CA-05: the same values change nothing: no Keycloak call, no audit, same version. */
	@Test
	void savingTheSameValuesChangesNothing() {
		User ana = insert("Ana Ríos", "guest", "ACTIVE");

		put(ana.id(), Map.of("version", 0, "fullName", "Ana Ríos ", "role", "guest")).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.version").isEqualTo(0);

		verifyNoInteractions(keycloak);
		assertThat(auditRows()).isEmpty();
	}

	/**
	 * CA-04: the last active administrator cannot leave the role, while another one can, which also runs
	 * the locking queries against the database.
	 */
	@Test
	void keepsAnActiveOperationsAdministrator() {
		User maria = insert("María Pérez", ADMIN, "ACTIVE");
		insert("Pedro Ruiz", ADMIN, "PENDING");

		put(maria.id(), Map.of("version", 0, "fullName", "María Pérez", "role", "guest")).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("LAST_OPERATIONS_ADMINISTRATOR")
			.jsonPath("$.detail").isEqualTo(
					"No se puede cambiar el rol: debe quedar al menos un Operations administrator activo. No se guardó ningún cambio.");
		verifyNoInteractions(keycloak);

		insert("Luis Gómez", ADMIN, "ACTIVE");
		put(maria.id(), Map.of("version", 0, "fullName", "María Pérez", "role", "guest")).expectStatus().isOk();
		assertThat(role(maria.id())).isEqualTo("guest");
	}

	/** CA-12: a blank name is reported with the other invalid fields, and nothing changes. */
	@Test
	void reportsEveryInvalidField() {
		User ana = insert("Ana Ríos", "guest", "ACTIVE");

		put(ana.id(), Map.of("fullName", "  ", "role", "boss")).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
			.jsonPath("$.errors[*].field").value(List.class,
					fields -> assertThat(fields).containsExactlyInAnyOrder("version", "fullName", "role"))
			.jsonPath("$.errors[?(@.field == 'fullName')].message").isEqualTo(List.of("Escribe el nombre completo."));
		put(ana.id(), Map.of("version", 0, "fullName", "Ana Ríos", "role", "npd-leader")).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[0].field").isEqualTo("employee")
			.jsonPath("$.errors[0].message").isEqualTo("Completa los datos de empleado del líder NPD.");
		verifyNoInteractions(keycloak);
	}

	/** CA-13: an NPD lead without employee record cannot be edited; its account must be deleted. */
	@Test
	void rejectsAnNpdLeadWithoutEmployeeRecord() {
		User omar = insert("Omar Delgado", "npd-leader", "ACTIVE");

		put(omar.id(), Map.of("version", 0, "fullName", "Omar D.", "role", "npd-leader")).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("NPD_LEAD_WITHOUT_EMPLOYEE")
			.jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("omar.delgado@example.com"));
		verifyNoInteractions(keycloak);
	}

	/** A form read before the last save is rejected with the current version. */
	@Test
	void rejectsAStaleVersion() {
		User ana = insert("Ana Ríos", "guest", "ACTIVE");
		jdbc.sql("UPDATE app_user SET version = 2 WHERE id = :id").param("id", ana.id()).update();

		put(ana.id(), Map.of("version", 1, "fullName", "Ana", "role", "guest")).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("CONCURRENT_MODIFICATION")
			.jsonPath("$.currentVersion").isEqualTo(2);
		verifyNoInteractions(keycloak);
	}

	/** Keycloak down is a 503 that leaves the user as it was. */
	@Test
	void reportsKeycloakUnavailable() {
		User ana = insert("Ana Ríos", "guest", "ACTIVE");
		doThrow(new IdentityProviderUnavailableException(new RuntimeException())).when(keycloak)
			.replaceRealmRole(any(), any());

		put(ana.id(), Map.of("version", 0, "fullName", "Ana Ríos", "role", "customer-success")).expectStatus()
			.isEqualTo(503)
			.expectBody()
			.jsonPath("$.code").isEqualTo("IDENTITY_PROVIDER_UNAVAILABLE");
		assertThat(role(ana.id())).isEqualTo("guest");
		assertThat(auditRows()).isEmpty();
	}

	/** An unknown user is a 404. */
	@Test
	void reportsAMissingUser() {
		put(999_999, Map.of("version", 0, "fullName", "Ana", "role", "guest")).expectStatus().isNotFound();
	}

	/** CA-06: only Operations administrators edit users; without a token the request is refused. */
	@ParameterizedTest
	@ValueSource(strings = { "npd-leader", "customer-success", "guest" })
	void onlyOperationsAdministratorsEditUsers(String role) {
		User ana = insert("Ana Ríos", "guest", "ACTIVE");
		Map<String, Object> body = Map.of("version", 0, "fullName", "Ana", "role", "guest");

		client.put().uri("/api/v1/access/users/" + ana.id()).header(AUTHORIZATION, "Bearer " + role)
			.contentType(APPLICATION_JSON).body(body).exchange()
			.expectStatus().isForbidden();
		client.put().uri("/api/v1/access/users/" + ana.id()).contentType(APPLICATION_JSON).body(body).exchange()
			.expectStatus().isUnauthorized();
		verifyNoInteractions(keycloak);
	}

	private User insert(String fullName, String role, String state) {
		UUID keycloakId = UUID.randomUUID();
		long id = jdbc.sql("""
				INSERT INTO app_user (keycloak_id, full_name, email, role, state, created_at, created_by)
				VALUES (:keycloakId, :name, :email, :role, :state, now(), 'test')
				RETURNING id""")
			.param("keycloakId", keycloakId)
			.param("name", fullName)
			.param("email", fullName.toLowerCase().replace(' ', '.').replace("í", "i").replace("é", "e") + "@example.com")
			.param("role", role)
			.param("state", state)
			.query(Long.class)
			.single();
		return new User(id, keycloakId);
	}

	private String role(long id) {
		return jdbc.sql("SELECT role FROM app_user WHERE id = :id").param("id", id).query(String.class).single();
	}

	private List<String> auditRows() {
		return jdbc.sql("""
				SELECT field || ': ' || old_value || ' -> ' || new_value || ' by ' || actor FROM audit_log
				WHERE entity_type = 'app_user'""")
			.query(String.class)
			.list();
	}

	private RestTestClient.ResponseSpec put(long id, Object body) {
		return client.put()
			.uri("/api/v1/access/users/" + id)
			.header(AUTHORIZATION, "Bearer " + ADMIN)
			.contentType(APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	private record User(long id, UUID keycloakId) {
	}

}
