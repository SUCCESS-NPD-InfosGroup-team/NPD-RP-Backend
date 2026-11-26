package com.npdinfosgroup.api.planner.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * "Editar proyecto" through the API (HU-14), end to end: the name and contract end rules, the version
 * check, the audit rows and the roles.
 * <p>
 * The requests run in the server's own transactions, so each test cleans up the rows it created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class ProjectUpdateControllerTest {

	static final String ADMIN = "operations-administrator";

	static final String PROJECTS = "/api/v1/planner/projects";

	static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	@Autowired
	RestTestClient client;

	@Autowired
	JdbcClient jdbc;

	long bancoId;

	long coreId;

	long otherId;

	long closedId;

	long expiredId;

	/** A client with an active, an expired and an inactive project, and another client with "PU Core". */
	@BeforeEach
	void createFixture() {
		bancoId = jdbc.sql("INSERT INTO client (name) VALUES ('PU BANCO') RETURNING id").query(Long.class).single();
		long tigoId = jdbc.sql("INSERT INTO client (name) VALUES ('PU TIGO') RETURNING id").query(Long.class).single();
		coreId = insertProject(bancoId, "PU Core", TODAY.plusDays(40), true);
		otherId = insertProject(bancoId, "PU Otro", null, true);
		closedId = insertProject(bancoId, "PU Cerrado", null, false);
		expiredId = insertProject(bancoId, "PU Vencido", TODAY.minusDays(5), true);
		insertProject(tigoId, "PU Movil", null, true);
	}

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log WHERE entity_type IN ('project', 'employee')").update();
		jdbc.sql("DELETE FROM employee WHERE code LIKE 'PU-%'").update();
		jdbc.sql("DELETE FROM job_position WHERE name = 'PU Posicion'").update();
		jdbc.sql("DELETE FROM project WHERE client_id IN (SELECT id FROM client WHERE name LIKE 'PU %')").update();
		jdbc.sql("DELETE FROM client WHERE name LIKE 'PU %'").update();
	}

	/** The name and the contract end change, the version goes up and each field is audited. */
	@Test
	void editsNameAndContractEnd() {
		String newEnd = TODAY.plusDays(60).toString();
		put(ADMIN, coreId, body(" PU Core Regional ", newEnd, 0)).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.project.name").isEqualTo("PU Core Regional")
			.jsonPath("$.project.contractEndDate").isEqualTo(newEnd)
			.jsonPath("$.project.client.name").isEqualTo("PU BANCO")
			.jsonPath("$.project.version").isEqualTo(1)
			.jsonPath("$.membersEndDateMoved").isEqualTo(0);

		assertThat(changes(coreId)).containsExactlyInAnyOrder("name: PU Core -> PU Core Regional",
				"contractEndDate: " + TODAY.plusDays(40) + " -> " + newEnd);
		assertThat(jdbc.sql("SELECT DISTINCT actor FROM audit_log WHERE entity_type = 'project'").query(String.class)
			.list()).containsExactly(TestSecurityConfiguration.usernameOf(ADMIN));
	}

	/**
	 * CA-04: the people whose end date was the old contract end move with it, each audited; indefinite
	 * people, people with another date and people on Baja keep theirs.
	 */
	@Test
	void movesThePeopleWithTheOldContractEnd() {
		LocalDate oldEnd = TODAY.plusDays(40);
		LocalDate newEnd = TODAY.plusDays(90);
		jdbc.sql("INSERT INTO job_position (name) VALUES ('PU Posicion')").update();
		long moved = insertEmployee("PU-1", "ACTIVO", oldEnd);
		insertEmployee("PU-2", "LICENCIA", null);
		insertEmployee("PU-3", "ACTIVO", TODAY.plusDays(10));
		insertEmployee("PU-4", "BAJA", oldEnd);

		put(ADMIN, coreId, body("PU Core", newEnd.toString(), 0)).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.membersEndDateMoved").isEqualTo(1);

		assertThat(jdbc.sql("SELECT code || '=' || coalesce(project_end_date::text, '-') FROM employee WHERE code LIKE 'PU-%'")
			.query(String.class)
			.list()).containsExactlyInAnyOrder("PU-1=" + newEnd, "PU-2=-", "PU-3=" + TODAY.plusDays(10), "PU-4=" + oldEnd);
		assertThat(jdbc.sql("""
				SELECT entity_id FROM audit_log
				WHERE entity_type = 'employee' AND field = 'assignment.endDate' AND old_value = :old AND new_value = :new""")
			.param("old", oldEnd.toString())
			.param("new", newEnd.toString())
			.query(Long.class)
			.list()).containsExactly(moved);
	}

	/** A contract that already ended can be kept while renaming, and the contract end can be removed. */
	@Test
	void keepsAPastContractEndAndRemovesIt() {
		put(ADMIN, expiredId, body("PU Vencido 2", TODAY.minusDays(5).toString(), 0)).expectStatus().isOk();

		put(ADMIN, expiredId, body("PU Vencido 2", null, 1)).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.project.contractEndDate").doesNotExist()
			.jsonPath("$.project.version").isEqualTo(2);
	}

	/** Every invalid field comes at once; a new date in the past is one of them. */
	@Test
	void rejectsInvalidFieldsAllAtOnce() {
		Map<String, Object> request = body(" ", TODAY.minusDays(1).toString(), null);
		put(ADMIN, coreId, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
			.jsonPath("$.errors[*].field").value(List.class,
					fields -> assertThat(fields).containsExactlyInAnyOrder("name", "contractEndDate", "version"))
			.jsonPath("$.errors[?(@.field == 'contractEndDate')].message")
			.isEqualTo(List.of("Ya pasó. Usa una fecha futura o déjala vacía."));
	}

	/**
	 * Another project of the client with the name, ignoring case, is a 409; the project's own name with
	 * another case and a name used in another client are allowed.
	 */
	@Test
	void checksNamesWithinTheClient() {
		put(ADMIN, coreId, body("pu otro", null, 0)).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("PROJECT_NAME_TAKEN")
			.jsonPath("$.existingProjectId").value(Number.class, id -> assertThat(id.longValue()).isEqualTo(otherId));

		put(ADMIN, coreId, body("PU CORE", null, 0)).expectStatus().isOk();
		put(ADMIN, otherId, body("PU Movil", null, 0)).expectStatus().isOk();
	}

	/** A form read before the last save gets a 409 with the current version and who saved it. */
	@Test
	void rejectsAStaleVersion() {
		put(ADMIN, coreId, body("PU Core 2", null, 0)).expectStatus().isOk();

		put(ADMIN, coreId, body("PU Core 3", null, 0)).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("CONCURRENT_MODIFICATION")
			.jsonPath("$.currentVersion").isEqualTo(1)
			.jsonPath("$.updatedBy").isEqualTo(TestSecurityConfiguration.usernameOf(ADMIN))
			.jsonPath("$.updatedAt").isNotEmpty();
	}

	/** An inactive project is not edited; saving the same values changes nothing and audits nothing. */
	@Test
	void rejectsInactiveProjectsAndIgnoresNoChanges() {
		put(ADMIN, closedId, body("PU Cerrado 2", null, 0)).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("PROJECT_INACTIVE");

		put(ADMIN, otherId, body("PU Otro", null, 0)).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.project.version").isEqualTo(0);
		assertThat(changes(otherId)).isEmpty();
	}

	/**
	 * A project without team is deactivated and reactivated, each change audited; repeating an action
	 * changes nothing.
	 */
	@Test
	void deactivatesAndReactivates() {
		activation("deactivation", ADMIN, otherId, 0).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.active").isEqualTo(false)
			.jsonPath("$.version").isEqualTo(1);
		activation("deactivation", ADMIN, otherId, 1).expectStatus().isOk().expectBody().jsonPath("$.version").isEqualTo(1);

		activation("reactivation", ADMIN, otherId, 1).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.active").isEqualTo(true)
			.jsonPath("$.version").isEqualTo(2);
		assertThat(changes(otherId)).containsExactly("active: Sí -> No", "active: No -> Sí");
	}

	/**
	 * A project with people is not deactivated (people on Baja do not count), and the imported
	 * «No especificado» project, whose client is inactive, is not reactivated.
	 */
	@Test
	void checksTheTeamAndTheClient() {
		jdbc.sql("INSERT INTO job_position (name) VALUES ('PU Posicion')").update();
		insertEmployee("PU-1", "ACTIVO", null);
		activation("deactivation", ADMIN, coreId, 0).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("PROJECT_HAS_TEAM")
			.jsonPath("$.teamSize").isEqualTo(1);
		jdbc.sql("UPDATE employee SET status_id = (SELECT id FROM resource_status WHERE code = 'BAJA') WHERE code = 'PU-1'")
			.update();
		activation("deactivation", ADMIN, coreId, 0).expectStatus().isOk();

		long notSpecified = jdbc.sql("SELECT p.id FROM project p JOIN client c ON c.id = p.client_id WHERE c.name = 'No especificado'")
			.query(Long.class)
			.single();
		activation("reactivation", ADMIN, notSpecified, 0).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[0].field").isEqualTo("clientId")
			.jsonPath("$.errors[0].code").isEqualTo("INACTIVE_CLIENT");
	}

	/** Only an Operations administrator deactivates or reactivates; the version is required. */
	@Test
	void checksActivationRolesAndVersion() {
		activation("deactivation", "guest", otherId, 0).expectStatus().isForbidden();
		activation("reactivation", "customer-success", closedId, 0).expectStatus().isForbidden();
		activation("reactivation", ADMIN, closedId, null).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[0].field").isEqualTo("version");
		activation("deactivation", ADMIN, 999_999, 0).expectStatus().isNotFound();
	}

	/** Only an Operations administrator edits; an unknown project is a 404. */
	@Test
	void checksRolesAndUnknownProject() {
		put("guest", coreId, body("PU X", null, 0)).expectStatus().isForbidden();
		put("npd-leader", coreId, body("PU X", null, 0)).expectStatus().isForbidden();
		client.put().uri(PROJECTS + "/{id}", coreId).contentType(APPLICATION_JSON).body(body("PU X", null, 0))
			.exchange().expectStatus().isUnauthorized();
		put(ADMIN, 999_999, body("PU X", null, 0)).expectStatus().isNotFound();
	}

	static Map<String, Object> body(String name, String contractEnd, Integer version) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("contractEndDate", contractEnd);
		body.put("version", version);
		return body;
	}

	RestTestClient.ResponseSpec put(String token, long id, Object body) {
		return client.put()
			.uri(PROJECTS + "/{id}", id)
			.header(AUTHORIZATION, "Bearer " + token)
			.contentType(APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	RestTestClient.ResponseSpec activation(String action, String token, long id, Integer version) {
		Map<String, Object> body = new HashMap<>();
		body.put("version", version);
		return client.post()
			.uri(PROJECTS + "/{id}/" + action, id)
			.header(AUTHORIZATION, "Bearer " + token)
			.contentType(APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	List<String> changes(long projectId) {
		return jdbc.sql("""
				SELECT field || ': ' || coalesce(old_value, '') || ' -> ' || coalesce(new_value, '') FROM audit_log
				WHERE entity_type = 'project' AND entity_id = :id AND action = 'UPDATE' ORDER BY id""")
			.param("id", projectId)
			.query(String.class)
			.list();
	}

	long insertEmployee(String code, String status, LocalDate projectEnd) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, hire_date, status_id, project_id,
				                      project_end_date)
				VALUES (:code, :code, (SELECT id FROM gender WHERE code = 'F'),
				        (SELECT id FROM job_position WHERE name = 'PU Posicion'), DATE '2024-01-15',
				        (SELECT id FROM resource_status WHERE code = :status), :project, :projectEnd)
				RETURNING id""")
			.param("code", code)
			.param("status", status)
			.param("project", coreId)
			.param("projectEnd", projectEnd, Types.DATE)
			.query(Long.class)
			.single();
	}

	long insertProject(long clientId, String name, LocalDate contractEnd, boolean active) {
		return jdbc.sql("""
				INSERT INTO project (client_id, name, contract_end_date, active)
				VALUES (:client, :name, :contractEnd, :active) RETURNING id""")
			.param("client", clientId)
			.param("name", name)
			.param("contractEnd", contractEnd, Types.DATE)
			.param("active", active)
			.query(Long.class)
			.single();
	}

}
