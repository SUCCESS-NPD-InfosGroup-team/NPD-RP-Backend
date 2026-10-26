package com.npdinfosgroup.api.planner.web;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.sql.Types;
import java.util.List;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The "Proyecto asignado" selector: the active projects of a client with their team size.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class ClientControllerTest {

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	private long clientId;

	private long emptyClientId;

	/**
	 * A client with two active projects (one with an employee on Baja) and an inactive one, and a
	 * client without projects.
	 */
	@BeforeEach
	void createFixture() {
		clientId = insert("INSERT INTO client (name) VALUES ('CL BANESCO') RETURNING id");
		emptyClientId = insert("INSERT INTO client (name) VALUES ('CL Sin proyectos') RETURNING id");
		long fusion = insertProject("CL Oracle Fusion", null, true);
		insertProject("CL Automatizacion", "2026-12-31", true);
		insertProject("CL Cerrado", null, false);
		jdbc.sql("INSERT INTO job_position (name) VALUES ('CL Posicion')").update();
		insertEmployee("CL-1", "ACTIVO", fusion);
		insertEmployee("CL-2", "BAJA", fusion);
	}

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM employee WHERE code LIKE 'CL-%'").update();
		jdbc.sql("DELETE FROM project WHERE name LIKE 'CL %'").update();
		jdbc.sql("DELETE FROM client WHERE name LIKE 'CL %'").update();
		jdbc.sql("DELETE FROM job_position WHERE name LIKE 'CL %'").update();
	}

	/** Only active projects, by name, with the team size (Baja not counted) and the contract end. */
	@Test
	void listsActiveProjects() {
		get(clientId, "Bearer customer-success").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("CL Automatizacion", "CL Oracle Fusion"))
			.jsonPath("$.items[0].teamSize").isEqualTo(0)
			.jsonPath("$.items[0].contractEndDate").isEqualTo("2026-12-31")
			.jsonPath("$.items[1].teamSize").isEqualTo(1)
			.jsonPath("$.items[1].contractEndDate").isEmpty()
			.jsonPath("$.items[1].id").isNumber();
	}

	/** A client without active projects gives an empty list, not an error. */
	@Test
	void clientWithoutProjects() {
		get(emptyClientId, "Bearer guest").expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty();
	}

	/** An unknown client is a 404; without a token, a 401. */
	@Test
	void unknownClientOrNoToken() {
		get(999_999, "Bearer guest").expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.code").isEqualTo("NOT_FOUND")
			.jsonPath("$.detail").isEqualTo("No existe el registro 999999.");

		client.get().uri("/api/v1/clients/{id}/projects", clientId).exchange().expectStatus().isUnauthorized();
	}

	private RestTestClient.ResponseSpec get(long id, String authorization) {
		return client.get().uri("/api/v1/clients/{id}/projects", id).header(AUTHORIZATION, authorization).exchange();
	}

	private long insert(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private long insertProject(String name, String contractEnd, boolean active) {
		return jdbc.sql("""
				INSERT INTO project (client_id, name, contract_end_date, active)
				VALUES (:client, :name, CAST(:contractEnd AS date), :active) RETURNING id""")
			.param("client", clientId)
			.param("name", name)
			.param("contractEnd", contractEnd, Types.VARCHAR)
			.param("active", active)
			.query(Long.class)
			.single();
	}

	private void insertEmployee(String code, String status, long project) {
		jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, hire_date, status_id, project_id)
				VALUES (:code, :code,
				        (SELECT id FROM gender WHERE code = 'F'),
				        (SELECT id FROM job_position WHERE name = 'CL Posicion'),
				        DATE '2024-01-15',
				        (SELECT id FROM resource_status WHERE code = :status),
				        :project)""")
			.param("code", code)
			.param("status", status)
			.param("project", project)
			.update();
	}

}
