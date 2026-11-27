package com.npdinfosgroup.api.planner.web;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.sql.Types;
import java.util.List;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Candidates of "Agregar colaboradores" (HU-08 CA-01), end to end: who is listed, in which order, the
 * search, the paging, security and the unknown project.
 * <p>
 * The target project has one member; the others are without project, on another project or on Baja. The
 * requests run in the server's own transactions, so each test cleans up the rows it created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class AssignmentCandidatesControllerTest {

	private static final String CANDIDATES = "/api/v1/planner/projects/{projectId}/candidates";

	private static final String ADMIN = "operations-administrator";

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	private long positionId;

	private long coreProjectId;

	/** Core QA with one member, two people without project, one on another project and one on Baja. */
	@BeforeEach
	void createPeople() {
		positionId = insert("INSERT INTO job_position (name) VALUES ('AC Analista') RETURNING id");
		long mercantil = insert("INSERT INTO client (name) VALUES ('AC MERCANTIL') RETURNING id");
		long copa = insert("INSERT INTO client (name) VALUES ('AC COPA') RETURNING id");
		coreProjectId = insertProject(mercantil, "AC Core QA");
		long comercialId = insertProject(copa, "AC Comercial");
		insertEmployee("1", "Lorena Batista", "ACTIVO", coreProjectId);
		insertEmployee("2", "Zoe Castillo", "ACTIVO", null);
		insertEmployee("3", "Ana Abrego", "LICENCIA", null);
		insertEmployee("4", "Héctor Zúñiga", "ACTIVO", comercialId);
		insertEmployee("5", "Pedro Salida", "BAJA", null);
	}

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM employee").update();
		jdbc.sql("DELETE FROM project WHERE name LIKE 'AC %'").update();
		jdbc.sql("DELETE FROM client WHERE name LIKE 'AC %'").update();
		jdbc.sql("DELETE FROM job_position WHERE name LIKE 'AC %'").update();
	}

	/** Without project first, by name, then the rest with their project; members and people on Baja are left out. */
	@Test
	void listsThePeopleWithoutProjectFirst() {
		get(ADMIN, "").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("Ana Abrego", "Zoe Castillo", "Héctor Zúñiga"))
			.jsonPath("$.items[0].project").doesNotExist()
			.jsonPath("$.items[0].status.code").isEqualTo("LICENCIA")
			.jsonPath("$.items[0].version").isEqualTo(0)
			.jsonPath("$.items[2].project.name").isEqualTo("AC Comercial")
			.jsonPath("$.items[2].client.name").isEqualTo("AC COPA")
			.jsonPath("$.withoutProject").isEqualTo(2)
			.jsonPath("$.page.totalElements").isEqualTo(3);
	}

	/** The search ignores case and accents; the order holds across pages and the count covers them all. */
	@Test
	void searchesAndPages() {
		get(ADMIN, "?q=ZUNIGA").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("Héctor Zúñiga"))
			.jsonPath("$.withoutProject").isEqualTo(0);
		get(ADMIN, "?page=1&size=2").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("Héctor Zúñiga"))
			.jsonPath("$.page.totalPages").isEqualTo(2)
			.jsonPath("$.withoutProject").isEqualTo(2);
	}

	/**
	 * Only the Operations administrator can read the candidates.
	 *
	 * @param role realm role used as the token
	 */
	@ParameterizedTest
	@ValueSource(strings = { "npd-leader", "customer-success", "guest" })
	void forbidsTheOtherRoles(String role) {
		get(role, "").expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
	}

	/** Without a token nothing is read; an unknown project is not found. */
	@Test
	void requiresATokenAndAnExistingProject() {
		client.get().uri(CANDIDATES, coreProjectId).exchange().expectStatus().isUnauthorized();
		client.get()
			.uri(CANDIDATES, 999_999_999)
			.header(AUTHORIZATION, "Bearer " + ADMIN)
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.code").isEqualTo("NOT_FOUND");
	}

	private RestTestClient.ResponseSpec get(String token, String query) {
		return client.get().uri(CANDIDATES + query, coreProjectId).header(AUTHORIZATION, "Bearer " + token).exchange();
	}

	private long insert(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private long insertProject(long clientId, String name) {
		return insert("INSERT INTO project (client_id, name) VALUES (" + clientId + ", '" + name + "') RETURNING id");
	}

	private long insertEmployee(String code, String name, String status, Long project) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, level, hire_date, on_probation,
				                      status_id, project_id)
				VALUES (:code, :name, (SELECT id FROM gender WHERE code = 'F'), :position, 3, DATE '2025-01-06', false,
				        (SELECT id FROM resource_status WHERE code = :status), :project)
				RETURNING id""")
			.param("code", "AC" + code)
			.param("name", name)
			.param("position", positionId)
			.param("status", status)
			.param("project", project, Types.BIGINT)
			.query(Long.class)
			.single();
	}

}
