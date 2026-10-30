package com.npdinfosgroup.api.planner.web;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
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
 * Searching and filtering the roster through the API (HU-03), end to end: the order of the groups,
 * their totals, every filter and the search text.
 * <p>
 * The roster has three projects (contract ends in 30 days, in 300 days and without date), people without
 * project and one on the imported "No especificado" project. The requests run in the server's own
 * transactions, so each test cleans up the rows it created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class ResourceSearchControllerTest {

	private static final String RESOURCES = "/api/v1/planner/resources";

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	private long positionId;

	private long functionalRoleId;

	private long automationRoleId;

	private long banescoId;

	private long banescoProjectId;

	private long copaProjectId;

	private long tigoProjectId;

	private long leaderId;

	/**
	 * Three clients with one project each, and eight people spread over them, without project, on
	 * "No especificado" and on Baja.
	 */
	@BeforeEach
	void createRoster() {
		positionId = insert("INSERT INTO job_position (name) VALUES ('RT Analista') RETURNING id");
		functionalRoleId = insert("INSERT INTO client_role (name) VALUES ('RT QA Funcional') RETURNING id");
		automationRoleId = insert("INSERT INTO client_role (name) VALUES ('RT QA Automatizador') RETURNING id");
		banescoId = insert("INSERT INTO client (name) VALUES ('RT BANESCO') RETURNING id");
		banescoProjectId = insertProject(banescoId, "RT Programa de automatización", LocalDate.of(2025, 2, 3),
				TODAY.plusDays(30));
		copaProjectId = insertProject(insert("INSERT INTO client (name) VALUES ('RT COPA') RETURNING id"),
				"RT Comercial", LocalDate.of(2023, 3, 1), TODAY.plusDays(300));
		tigoProjectId = insertProject(insert("INSERT INTO client (name) VALUES ('RT TIGO') RETURNING id"), "RT Tigo",
				LocalDate.of(2024, 8, 1), null);
		long notSpecified = jdbc.sql("SELECT id FROM project WHERE name = 'No especificado'").query(Long.class).single();

		leaderId = insertEmployee("755", "Lorena Batista", "ACTIVO", banescoProjectId, null, functionalRoleId, 4,
				LocalDate.of(2024, 3, 1), TODAY.plusDays(100), false);
		insertEmployee("1045", "Ana Torres", "ACTIVO", banescoProjectId, leaderId, functionalRoleId, 3,
				LocalDate.of(2026, 8, 3), TODAY.plusDays(24), true);
		insertEmployee("1052", "Luis Barría", "LICENCIA", banescoProjectId, leaderId, automationRoleId, 3,
				LocalDate.of(2025, 2, 10), TODAY.minusDays(7), false);
		insertEmployee("612", "Daniela Guerra", "ACTIVO", copaProjectId, leaderId, automationRoleId, 2,
				LocalDate.of(2020, 1, 1), TODAY.plusDays(60), false);
		insertEmployee("957", "Héctor Zúñiga", "ACTIVO", tigoProjectId, null, functionalRoleId, 3,
				LocalDate.of(2024, 10, 1), null, false);
		insertEmployee("1092", "Rocío Batista", "ACTIVO", null, leaderId, null, 2, LocalDate.of(2025, 1, 6), null,
				false);
		insertEmployee("700", "Pedro Salida", "BAJA", null, null, null, 3, LocalDate.of(2023, 5, 2), null, false);
		insertEmployee("1015", "Fernanda Ríos", "ACTIVO", notSpecified, null, functionalRoleId, 3,
				LocalDate.of(2025, 10, 1), TODAY.plusDays(80), false);
	}

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("UPDATE employee SET leader_id = NULL").update();
		jdbc.sql("DELETE FROM employee").update();
		jdbc.sql("DELETE FROM project WHERE name LIKE 'RT %'").update();
		jdbc.sql("DELETE FROM client WHERE name LIKE 'RT %'").update();
		jdbc.sql("DELETE FROM job_position WHERE name LIKE 'RT %'").update();
		jdbc.sql("DELETE FROM client_role WHERE name LIKE 'RT %'").update();
	}

	/**
	 * Without filters: "No especificado" first, then the people without project, then the projects whose
	 * contract ends soonest (no contract end last). Inside a project, whoever leaves first goes first.
	 */
	@Test
	void ordersGroupsAndPeople() {
		search("", "guest").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name")
			.isEqualTo(List.of("Fernanda Ríos", "Pedro Salida", "Rocío Batista", "Luis Barría", "Ana Torres",
					"Lorena Batista", "Daniela Guerra", "Héctor Zúñiga"))
			.jsonPath("$.groups[*].type")
			.isEqualTo(List.of("NOT_SPECIFIED", "NO_PROJECT", "PROJECT", "PROJECT", "PROJECT"))
			.jsonPath("$.groups[*].project")
			.isEqualTo(Arrays.asList("No especificado", null, "RT Programa de automatización", "RT Comercial", "RT Tigo"))
			.jsonPath("$.groups[*].total").isEqualTo(List.of(1, 2, 3, 1, 1))
			.jsonPath("$.groups[2].client.name").isEqualTo("RT BANESCO")
			.jsonPath("$.groups[2].contractEndDate").isEqualTo(TODAY.plusDays(30).toString())
			.jsonPath("$.page.totalElements").isEqualTo(8);
	}

	/**
	 * Each row carries what the timeline needs: where the bar starts (the later of the project creation and
	 * the hire date), the end date, the days left, "indefinido" and probation.
	 */
	@Test
	void describesEachRow() {
		search("?q=1045", "npd-leader").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[0].employeeNumber").isEqualTo("1045")
			.jsonPath("$.items[0].level").isEqualTo(3)
			.jsonPath("$.items[0].clientRole").isEqualTo("RT QA Funcional")
			.jsonPath("$.items[0].leader.name").isEqualTo("Lorena Batista")
			.jsonPath("$.items[0].status.label").isEqualTo("Activo")
			.jsonPath("$.items[0].situation.label").isEqualTo("Asignado")
			.jsonPath("$.items[0].onProbation").isEqualTo(true)
			.jsonPath("$.items[0].probationEndDate").isEqualTo("2026-11-01")
			.jsonPath("$.items[0].assignmentStart").isEqualTo("2026-08-03")
			.jsonPath("$.items[0].assignmentEnd").isEqualTo(TODAY.plusDays(24).toString())
			.jsonPath("$.items[0].daysToAssignmentEnd").isEqualTo(24)
			.jsonPath("$.items[0].indefinite").isEqualTo(false);

		search("?q=612", "guest").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[0].assignmentStart").isEqualTo("2023-03-01");

		search("?q=957", "guest").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[0].indefinite").isEqualTo(true)
			.jsonPath("$.items[0].assignmentEnd").doesNotExist()
			.jsonPath("$.items[0].daysToAssignmentEnd").doesNotExist();

		search("?q=1092", "guest").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[0].situation.code").isEqualTo("SIN_PROYECTO")
			.jsonPath("$.items[0].projectId").doesNotExist()
			.jsonPath("$.items[0].assignmentStart").doesNotExist()
			.jsonPath("$.items[0].indefinite").isEqualTo(false);
	}

	/** A group cut by the page end still reports its total in every page. */
	@Test
	void keepsGroupTotalsAcrossPages() {
		search("?size=4&page=0", "guest").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name")
			.isEqualTo(List.of("Fernanda Ríos", "Pedro Salida", "Rocío Batista", "Luis Barría"))
			.jsonPath("$.groups[2].total").isEqualTo(3)
			.jsonPath("$.page.totalPages").isEqualTo(2);

		search("?size=4&page=1", "guest").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name")
			.isEqualTo(List.of("Ana Torres", "Lorena Batista", "Daniela Guerra", "Héctor Zúñiga"))
			.jsonPath("$.groups[0].project").isEqualTo("RT Programa de automatización")
			.jsonPath("$.groups[0].total").isEqualTo(3);
	}

	/**
	 * CA-01: client and status together return only the rows that match both. "Activo" on the screen
	 * includes Licencia.
	 */
	@Test
	void combinesFilters() {
		search("?clientId=" + banescoId + "&status=ACTIVO", "operations-administrator").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("Ana Torres", "Lorena Batista"));

		search("?clientId=" + banescoId + "&status=ACTIVO&status=licencia", "guest").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("Luis Barría", "Ana Torres", "Lorena Batista"));
	}

	/** Every other filter: project, NPD lead, role, level and situation. */
	@Test
	void filtersByEveryCriterion() {
		expectNames("?projectId=" + copaProjectId, "Daniela Guerra");
		expectNames("?leaderId=" + leaderId, "Rocío Batista", "Luis Barría", "Ana Torres", "Daniela Guerra");
		expectNames("?clientRoleId=" + automationRoleId, "Luis Barría", "Daniela Guerra");
		expectNames("?level=2", "Rocío Batista", "Daniela Guerra");
		expectNames("?situation=PENDIENTE_CONFIRMACION", "Luis Barría");
		expectNames("?situation=SIN_PROYECTO&status=ACTIVO", "Rocío Batista");
		expectNames("?projectId=" + tigoProjectId + "&level=4");
	}

	/**
	 * The text is searched in name, employee number, project and client, ignoring case, accents and
	 * extra spaces; wildcards are taken literally.
	 */
	@Test
	void searchesTextWithoutAccents() {
		expectText("zuniga", "Héctor Zúñiga");
		expectText("  BATISTA ", "Rocío Batista", "Lorena Batista");
		expectText("rocio", "Rocío Batista");
		expectText("1052", "Luis Barría");
		expectText("copa", "Daniela Guerra");
		expectText("automatizacion", "Luis Barría", "Ana Torres", "Lorena Batista");
		expectText("%");
		expectText("_");
	}

	/** Unknown statuses or situations are rejected instead of returning an empty list. */
	@Test
	void rejectsUnknownValues() {
		search("?status=ACTIVE&situation=ASSIGNED", "guest").expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
			.jsonPath("$.errors[*].field").isEqualTo(List.of("status", "situation"));
	}

	/** The roster needs a valid token. */
	@Test
	void requiresToken() {
		client.get().uri(RESOURCES).exchange().expectStatus().isUnauthorized();
	}

	private void expectNames(String query, String... names) {
		search(query, "guest").expectStatus().isOk().expectBody().jsonPath("$.items[*].name").isEqualTo(List.of(names));
	}

	private void expectText(String text, String... names) {
		client.get()
			.uri(builder -> builder.path(RESOURCES).queryParam("q", "{q}").build(text))
			.header(AUTHORIZATION, "Bearer guest")
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of(names));
	}

	private RestTestClient.ResponseSpec search(String query, String token) {
		return client.get().uri(RESOURCES + query).header(AUTHORIZATION, "Bearer " + token).exchange();
	}

	private long insert(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private long insertProject(long clientId, String name, LocalDate createdOn, LocalDate contractEnd) {
		return jdbc.sql("""
				INSERT INTO project (client_id, name, created_at, contract_end_date)
				VALUES (:client, :name, :created, :contractEnd) RETURNING id""")
			.param("client", clientId)
			.param("name", name)
			.param("created", createdOn.atStartOfDay(ZoneId.of("America/Bogota")).toOffsetDateTime())
			.param("contractEnd", contractEnd, Types.DATE)
			.query(Long.class)
			.single();
	}

	private long insertEmployee(String code, String name, String status, Long project, Long leader, Long clientRole,
			int level, LocalDate hireDate, LocalDate endDate, boolean onProbation) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, level, hire_date, on_probation,
				                      status_id, project_id, leader_id, client_role_id, project_end_date)
				VALUES (:code, :name, (SELECT id FROM gender WHERE code = 'F'), :position, :level, :hireDate,
				        :onProbation, (SELECT id FROM resource_status WHERE code = :status), :project, :leader,
				        :clientRole, :endDate)
				RETURNING id""")
			.param("code", code)
			.param("name", name)
			.param("position", positionId)
			.param("level", level)
			.param("hireDate", hireDate)
			.param("onProbation", onProbation)
			.param("status", status)
			.param("project", project, Types.BIGINT)
			.param("leader", leader, Types.BIGINT)
			.param("clientRole", clientRole, Types.BIGINT)
			.param("endDate", endDate, Types.DATE)
			.query(Long.class)
			.single();
	}

}
