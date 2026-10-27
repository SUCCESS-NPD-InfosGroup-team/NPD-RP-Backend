package com.npdinfosgroup.api.planner.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
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
 * Projects through the API (HU-07), end to end: the list with its filters and counts, creating a
 * project for an existing or a new client, and reading one.
 * <p>
 * The requests run in the server's own transactions, so each test cleans up the rows it created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class ProjectControllerTest {

	private static final String ADMIN = "operations-administrator";

	private static final String PROJECTS = "/api/v1/planner/projects";

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	private long banescoId;

	private long tigoId;

	private long inactiveClientId;

	private long portalId;

	private long fusionId;

	private long leadId;

	/**
	 * Two clients with projects (one inactive project), an inactive client, an NPD lead with a
	 * collaborator on "PJ Oracle Fusion" and one collaborator on Baja there.
	 */
	@BeforeEach
	void createFixture() {
		banescoId = insert("INSERT INTO client (name) VALUES ('PJ BANESCO') RETURNING id");
		tigoId = insert("INSERT INTO client (name, is_internal) VALUES ('PJ TIGO', true) RETURNING id");
		inactiveClientId = insert("INSERT INTO client (name, active) VALUES ('PJ Antiguo', false) RETURNING id");
		portalId = insertProject(banescoId, "PJ Portal", TODAY.plusDays(20), true);
		fusionId = insertProject(banescoId, "PJ Oracle Fusion", null, true);
		insertProject(banescoId, "PJ Cerrado", null, false);
		insertProject(tigoId, "PJ Soporte", TODAY.plusDays(90), true);
		jdbc.sql("INSERT INTO job_position (name) VALUES ('PJ Posicion')").update();
		leadId = insertEmployee("PJ-1", "PJ Lorena Batista", "ACTIVO", null, null, null);
		insertEmployee("PJ-2", "PJ Laura Gomez", "ACTIVO", fusionId, leadId, TODAY.minusDays(1));
		insertEmployee("PJ-3", "PJ Mario Ruiz", "BAJA", portalId, leadId, null);
	}

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log WHERE entity_type IN ('client', 'project')").update();
		jdbc.sql("UPDATE employee SET leader_id = NULL WHERE code LIKE 'PJ-%'").update();
		jdbc.sql("DELETE FROM employee WHERE code LIKE 'PJ-%'").update();
		jdbc.sql("DELETE FROM project WHERE client_id IN (SELECT id FROM client WHERE name LIKE 'PJ %')").update();
		jdbc.sql("DELETE FROM client WHERE name LIKE 'PJ %'").update();
		jdbc.sql("DELETE FROM job_position WHERE name LIKE 'PJ %'").update();
	}

	/**
	 * The list goes by client and then by project, leaves the inactive projects out, counts the team
	 * without Baja and gives the days to the contract end and the leads.
	 */
	@Test
	void listsProjectsByClientWithCounts() {
		list("guest", "").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[?(@.name =~ /PJ .*/)].name")
			.isEqualTo(List.of("PJ Oracle Fusion", "PJ Portal", "PJ Soporte"))
			.jsonPath("$.items[?(@.name == 'PJ Oracle Fusion')].teamSize").isEqualTo(List.of(1))
			.jsonPath("$.items[?(@.name == 'PJ Oracle Fusion')].pendingConfirmation").isEqualTo(List.of(1))
			.jsonPath("$.items[?(@.name == 'PJ Oracle Fusion')].leads[*].name").isEqualTo(List.of("PJ Lorena Batista"))
			.jsonPath("$.items[?(@.name == 'PJ Oracle Fusion')].daysToContractEnd").isEqualTo(Collections.singletonList(null))
			.jsonPath("$.items[?(@.name == 'PJ Portal')].teamSize").isEqualTo(List.of(0))
			.jsonPath("$.items[?(@.name == 'PJ Portal')].leads").isEqualTo(List.of(List.of()))
			.jsonPath("$.items[?(@.name == 'PJ Portal')].daysToContractEnd").isEqualTo(List.of(20))
			.jsonPath("$.items[?(@.name == 'PJ Soporte')].client.isInternal").isEqualTo(List.of(true))
			.jsonPath("$.page.size").isEqualTo(25);
	}

	/** Each filter narrows the list; the inactive projects come only when asked for. */
	@Test
	void filtersTheList() {
		list("guest", "clientId=" + banescoId + "&includeInactive=true").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("PJ Cerrado", "PJ Oracle Fusion", "PJ Portal"))
			.jsonPath("$.items[0].active").isEqualTo(false);

		// The lead's collaborator on Baja does not make "PJ Portal" one of their projects
		list("guest", "leaderId=" + leadId).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("PJ Oracle Fusion"));

		list("guest", "clientId=" + banescoId + "&contractEndBefore=" + TODAY.plusDays(30)).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("PJ Portal"));

		list("guest", "clientId=" + inactiveClientId).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items").isEmpty()
			.jsonPath("$.page.totalElements").isEqualTo(0);
	}

	/** Pages are numbered from 0 and report the totals. */
	@Test
	void paginatesTheList() {
		list("guest", "clientId=" + banescoId + "&page=1&size=1").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("PJ Portal"))
			.jsonPath("$.page.number").isEqualTo(1)
			.jsonPath("$.page.size").isEqualTo(1)
			.jsonPath("$.page.totalElements").isEqualTo(2)
			.jsonPath("$.page.totalPages").isEqualTo(2);
	}

	/**
	 * A project for an existing client is created active and without team, can be read at its location
	 * and leaves its initial values in the audit log.
	 */
	@Test
	void createsProjectForExistingClient() {
		// A contract that ends today has not passed yet
		String contractEnd = TODAY.toString();
		Map<String, Object> request = new HashMap<>();
		request.put("clientId", banescoId);
		request.put("name", "  PJ Banca movil ");
		request.put("contractEndDate", contractEnd);

		URI location = post(ADMIN, request).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.name").isEqualTo("PJ Banca movil")
			.jsonPath("$.client.id").value(Number.class, id -> assertThat(id.longValue()).isEqualTo(banescoId))
			.jsonPath("$.client.name").isEqualTo("PJ BANESCO")
			.jsonPath("$.client.isInternal").isEqualTo(false)
			.jsonPath("$.active").isEqualTo(true)
			.jsonPath("$.startDate").isNotEmpty()
			.jsonPath("$.contractEndDate").isEqualTo(contractEnd)
			.jsonPath("$.teamSize").isEqualTo(0)
			.returnResult()
			.getResponseHeaders()
			.getLocation();

		long id = jdbc.sql("SELECT id FROM project WHERE name = 'PJ Banca movil'").query(Long.class).single();
		assertThat(location).hasPath(PROJECTS + "/" + id);
		client.get().uri(location).header(AUTHORIZATION, "Bearer guest").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.name").isEqualTo("PJ Banca movil");
		assertThat(auditValues("project", id)).containsExactlyInAnyOrder("client=PJ BANESCO", "name=PJ Banca movil",
				"contractEndDate=" + contractEnd);
		assertThat(jdbc.sql("SELECT DISTINCT actor FROM audit_log WHERE entity_type = 'project'").query(String.class)
			.list()).containsExactly(TestSecurityConfiguration.usernameOf(ADMIN));
	}

	/** A new client is created together with its project, and both are audited. */
	@Test
	void createsProjectWithNewClient() {
		Map<String, Object> request = new HashMap<>();
		request.put("newClient", Map.of("name", " PJ Claro ", "isInternal", true));
		request.put("name", "PJ Soporte QA");

		post(ADMIN, request).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.client.name").isEqualTo("PJ Claro")
			.jsonPath("$.client.isInternal").isEqualTo(true)
			.jsonPath("$.contractEndDate").doesNotExist();

		long clientId = jdbc.sql("SELECT id FROM client WHERE name = 'PJ Claro'").query(Long.class).single();
		assertThat(auditValues("client", clientId)).containsExactlyInAnyOrder("name=PJ Claro", "isInternal=Sí");
		client.get().uri("/api/v1/planner/clients/{id}/projects", clientId).header(AUTHORIZATION, "Bearer guest").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("PJ Soporte QA"));
	}

	/** Every invalid field comes at once, with the wording of the project form. */
	@Test
	void rejectsInvalidFieldsAllAtOnce() {
		post(ADMIN, Map.of()).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
			.jsonPath("$.errors[*].field").value(List.class,
					fields -> assertThat(fields).containsExactlyInAnyOrder("name", "clientId"))
			.jsonPath("$.errors[?(@.field == 'name')].message").isEqualTo(List.of("Escribe el nombre del proyecto."))
			.jsonPath("$.errors[?(@.field == 'clientId')].message")
			.isEqualTo(List.of("Selecciona un cliente o crea uno nuevo."));

		assertFieldError(Map.of("clientId", banescoId, "newClient", Map.of("name", "PJ X"), "name", "PJ Y"), "clientId",
				"CONFLICTING_VALUES");
		assertFieldError(Map.of("clientId", 999_999, "name", "PJ Y"), "clientId", "UNKNOWN_ID");
		assertFieldError(Map.of("clientId", inactiveClientId, "name", "PJ Y"), "clientId", "INACTIVE_CLIENT");
		assertFieldError(Map.of("newClient", Map.of("name", " "), "name", "PJ Y"), "newClient.name", "REQUIRED");
		assertFieldError(Map.of("clientId", banescoId, "name", "x".repeat(151)), "name", "TOO_LONG");
		assertFieldError(Map.of("clientId", banescoId, "name", "PJ Y", "contractEndDate", TODAY.minusDays(1).toString()),
				"contractEndDate", "DATE_IN_PAST");
		assertThat(jdbc.sql("SELECT count(*) FROM project WHERE name = 'PJ Y'").query(Long.class).single()).isZero();
	}

	/** A taken name, ignoring case, is a 409 that points to the existing record. */
	@Test
	void rejectsTakenNames() {
		post(ADMIN, Map.of("clientId", banescoId, "name", "pj portal")).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("PROJECT_NAME_TAKEN")
			.jsonPath("$.detail").isEqualTo("El cliente PJ BANESCO ya tiene un proyecto llamado «PJ Portal».")
			.jsonPath("$.existingProjectId").value(Number.class, id -> assertThat(id.longValue()).isEqualTo(portalId));

		post(ADMIN, Map.of("newClient", Map.of("name", "pj tigo"), "name", "PJ Nuevo")).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("CLIENT_NAME_TAKEN")
			.jsonPath("$.existingClientId").value(Number.class, id -> assertThat(id.longValue()).isEqualTo(tigoId));
	}

	/** Only an Operations administrator creates projects; everybody with a token reads them. */
	@Test
	void checksRolesAndUnknownProject() {
		post("guest", Map.of("clientId", banescoId, "name", "PJ Y")).expectStatus().isForbidden();
		client.get().uri(PROJECTS).exchange().expectStatus().isUnauthorized();
		client.get().uri(PROJECTS + "/{id}", portalId).header(AUTHORIZATION, "Bearer customer-success").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.teamSize").isEqualTo(0)
			.jsonPath("$.contractEndDate").isEqualTo(TODAY.plusDays(20).toString());
		client.get().uri(PROJECTS + "/999999").header(AUTHORIZATION, "Bearer guest").exchange()
			.expectStatus().isNotFound();
	}

	private void assertFieldError(Map<String, Object> request, String field, String code) {
		post(ADMIN, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[*].field").isEqualTo(List.of(field))
			.jsonPath("$.errors[0].code").isEqualTo(code);
	}

	private RestTestClient.ResponseSpec list(String token, String query) {
		return client.get().uri(PROJECTS + "?" + query).header(AUTHORIZATION, "Bearer " + token).exchange();
	}

	private RestTestClient.ResponseSpec post(String token, Object body) {
		return client.post()
			.uri(PROJECTS)
			.header(AUTHORIZATION, "Bearer " + token)
			.contentType(APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	private List<String> auditValues(String entityType, long id) {
		return jdbc.sql("""
				SELECT field || '=' || new_value FROM audit_log
				WHERE entity_type = :type AND entity_id = :id AND action = 'CREATE' AND old_value IS NULL""")
			.param("type", entityType)
			.param("id", id)
			.query(String.class)
			.list();
	}

	private long insert(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private long insertProject(long clientId, String name, LocalDate contractEnd, boolean active) {
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

	private long insertEmployee(String code, String name, String status, Long project, Long leader,
			LocalDate projectEnd) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, hire_date, status_id, project_id,
				                      leader_id, project_end_date)
				VALUES (:code, :name,
				        (SELECT id FROM gender WHERE code = 'F'),
				        (SELECT id FROM job_position WHERE name = 'PJ Posicion'),
				        DATE '2024-01-15',
				        (SELECT id FROM resource_status WHERE code = :status),
				        :project, :leader, :projectEnd)
				RETURNING id""")
			.param("code", code)
			.param("name", name)
			.param("status", status)
			.param("project", project, Types.BIGINT)
			.param("leader", leader, Types.BIGINT)
			.param("projectEnd", projectEnd, Types.DATE)
			.query(Long.class)
			.single();
	}

}
