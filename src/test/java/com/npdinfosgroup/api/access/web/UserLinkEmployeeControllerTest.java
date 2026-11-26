package com.npdinfosgroup.api.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * SCRUM-53 CA-10 with HU-10 CA-05 to CA-07, CA-12, CA-13: a new NPD lead who is already an employee.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class UserLinkEmployeeControllerTest {

	private static final String EMAIL = "kathya@example.com";

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private KeycloakAdminClient keycloak;

	private long positionId;

	private long userId;

	/** A position of the test and a Customer Success user. */
	@BeforeEach
	void createUser() {
		positionId = jdbc.sql("INSERT INTO job_position (name) VALUES ('UL Posicion') RETURNING id").query(Long.class).single();
		userId = insertUser(EMAIL, null);
	}

	/** Deletes the rows of the test. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log WHERE entity_type IN ('app_user', 'employee')").update();
		jdbc.sql("DELETE FROM app_user").update();
		jdbc.sql("DELETE FROM employee WHERE code LIKE 'UL-%'").update();
		jdbc.sql("DELETE FROM job_position WHERE name = 'UL Posicion'").update();
	}

	/** HU-10 CA-05: same email: 409 to confirm (also for a stale confirmation), then linked unchanged. */
	@Test
	void linksTheEmployeeWithTheSameEmail() {
		long employeeId = insertEmployee("UL-1", EMAIL, true, "ACTIVO");

		put(body("UL-7", employeeId + 1000)).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("EXISTING_NPD_LEAD_EMPLOYEE")
			.jsonPath("$.matchedBy").isEqualTo("EMAIL")
			.jsonPath("$.existingEmployee.id").isEqualTo(Math.toIntExact(employeeId))
			.jsonPath("$.existingEmployee.position").isEqualTo("UL Posicion");

		put(body("UL-7", employeeId)).expectStatus().isOk().expectBody()
			.jsonPath("$.employee.id").isEqualTo(Math.toIntExact(employeeId));
		assertThat(jdbc.sql("SELECT count(*) FROM employee WHERE code LIKE 'UL-%'").query(Long.class).single()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT full_name FROM employee WHERE id = :id").param("id", employeeId)
			.query(String.class).single()).isEqualTo("UL-1");
	}

	/** HU-10 CA-06: matched by number, the confirmed link fills the empty email of the record. */
	@Test
	void linksTheEmployeeWithTheSameNumber() {
		long employeeId = insertEmployee("UL-1", null, true, "LICENCIA");

		put(body("ul-1", null)).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.matchedBy").isEqualTo("EMPLOYEE_NUMBER");
		put(body("UL-1", employeeId)).expectStatus().isOk();

		assertThat(jdbc.sql("SELECT email FROM employee WHERE id = :id").param("id", employeeId).query(String.class)
			.single()).isEqualTo(EMAIL);
		assertThat(jdbc.sql("SELECT employee_id FROM app_user WHERE id = :id").param("id", userId).query(Long.class)
			.single()).isEqualTo(employeeId);
	}

	/** HU-10 CA-07, CA-12, CA-13: not an NPD lead, on Baja or with a user: not linked, and why. */
	@ParameterizedTest
	@CsvSource({ "false, ACTIVO, false, NOT_NPD_LEAD, no es líder NPD",
			"true, BAJA, false, TERMINATED, está de Baja",
			"true, ACTIVO, true, HAS_USER, ya tiene otro usuario" })
	void explainsWhyTheEmployeeCannotBeLinked(boolean npdLead, String status, boolean hasUser, String reason,
			String why) {
		long employeeId = insertEmployee("UL-1", EMAIL, npdLead, status);
		if (hasUser) {
			insertUser("otra@example.com", employeeId);
		}

		put(body("UL-7", employeeId)).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("EMPLOYEE_NOT_LINKABLE")
			.jsonPath("$.reason").isEqualTo(reason)
			.jsonPath("$.matchedBy").isEqualTo("EMAIL")
			.jsonPath("$.detail").value(String.class, detail -> assertThat(detail).contains("porque " + why + "."));
		assertThat(jdbc.sql("SELECT role FROM app_user WHERE id = :id").param("id", userId).query(String.class)
			.single()).isEqualTo("customer-success");
	}

	private Map<String, Object> body(String number, Long linkEmployeeId) {
		Map<String, Object> employee = Map.of("employeeNumber", number, "genderId", catalogId("gender", "F"),
				"positionId", positionId, "workModeId", catalogId("work_modality", "VIRTUAL"), "hireDate", "2026-03-03",
				"status", "ACTIVO");
		Map<String, Object> body = new HashMap<>(Map.of("version", 0, "fullName", "Kathya Solís", "role", "npd-leader",
				"employee", employee));
		body.put("linkEmployeeId", linkEmployeeId);
		return body;
	}

	private long insertUser(String email, Long employeeId) {
		return jdbc.sql("""
				INSERT INTO app_user (keycloak_id, full_name, email, role, state, created_at, created_by, employee_id)
				VALUES (gen_random_uuid(), 'Kathya Solís', :email, 'customer-success', 'ACTIVE', now(), 'test', :employee)
				RETURNING id""")
			.param("email", email)
			.param("employee", employeeId, Types.BIGINT)
			.query(Long.class)
			.single();
	}

	private long insertEmployee(String code, String email, boolean npdLead, String status) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, email, gender_id, job_position_id, hire_date, status_id,
				                      termination_date, termination_reason_id, is_npd_lead)
				VALUES (:code, :code, :email, (SELECT id FROM gender WHERE code = 'F'), :position, DATE '2024-01-15',
				        (SELECT id FROM resource_status WHERE code = :status),
				        CASE WHEN :status = 'BAJA' THEN DATE '2026-05-01' END,
				        CASE WHEN :status = 'BAJA' THEN (SELECT min(id) FROM termination_reason WHERE active) END, :npdLead)
				RETURNING id""")
			.param("code", code)
			.param("email", email, Types.VARCHAR)
			.param("position", positionId)
			.param("status", status)
			.param("npdLead", npdLead)
			.query(Long.class)
			.single();
	}

	private short catalogId(String table, String code) {
		return jdbc.sql("SELECT id FROM " + table + " WHERE code = :code").param("code", code).query(Short.class).single();
	}

	private RestTestClient.ResponseSpec put(Object body) {
		return client.put()
			.uri("/api/v1/access/users/" + userId)
			.header(AUTHORIZATION, "Bearer operations-administrator")
			.contentType(APPLICATION_JSON)
			.body(body)
			.exchange();
	}

}
