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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Editing a resource through the API (HU-02), end to end: security, validation, the version check,
 * the saved record and the audit log.
 * <p>
 * The requests run in the server's own transactions, so each test cleans up the rows it created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class ResourceUpdateControllerTest {

	private static final String ADMIN = "operations-administrator";

	private static final String RESOURCES = "/api/v1/planner/resources/";

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	private static final LocalDate HIRE_DATE = LocalDate.of(2024, 1, 15);

	private static final LocalDate END_DATE = TODAY.plusMonths(3);

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	private long positionId;

	private long clientRoleId;

	private long projectId;

	private long otherProjectId;

	private long notSpecifiedProjectId;

	private long leaderId;

	private short genderId;

	private short workModeId;

	private short notSpecifiedWorkModeId;

	private short equipmentId;

	private short reasonId;

	/** The edited resource: Ana Torres, on the first project of RT BANESCO, led by Lorena Batista. */
	private long resourceId;

	/**
	 * Two clients with a project each, an NPD lead and the resource that the tests edit.
	 */
	@BeforeEach
	void createFixture() {
		positionId = insert("INSERT INTO job_position (name) VALUES ('RT Analista de pruebas') RETURNING id");
		clientRoleId = insert("INSERT INTO client_role (name) VALUES ('RT QA Funcional') RETURNING id");
		projectId = insertProject("RT BANESCO", "RT Programa de automatización");
		otherProjectId = insertProject("RT COPA", "RT Portal de reservas");
		notSpecifiedProjectId = jdbc.sql("SELECT id FROM project WHERE name = 'No especificado'")
			.query(Long.class)
			.single();
		genderId = catalogId("gender", "F");
		workModeId = catalogId("work_modality", "VIRTUAL");
		notSpecifiedWorkModeId = catalogId("work_modality", "NO_ESPECIFICADO");
		equipmentId = catalogId("equipment_provider", "CLIENTE");
		reasonId = catalogId("termination_reason", "RENUNCIA_VOLUNTARIA");
		leaderId = insertEmployee("RT-L1", "Lorena Batista", workModeId, null, null);
		resourceId = insertEmployee("RT-1", "Ana Torres", workModeId, projectId, leaderId);
	}

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log").update();
		jdbc.sql("UPDATE employee SET leader_id = NULL").update();
		jdbc.sql("DELETE FROM employee").update();
		jdbc.sql("DELETE FROM project WHERE name LIKE 'RT %'").update();
		jdbc.sql("DELETE FROM client WHERE name LIKE 'RT %'").update();
		jdbc.sql("DELETE FROM job_position WHERE name LIKE 'RT %'").update();
		jdbc.sql("DELETE FROM client_role WHERE name LIKE 'RT %'").update();
	}

	/**
	 * CA-01, CA-03, CA-06: the edit is saved with a new version and its author, and the audit log keeps
	 * one row per changed field, with the previous and the new value. Unchanged fields are not logged.
	 */
	@Test
	void savesEditAndAuditsOnlyTheChangedFields() {
		Map<String, Object> request = currentRecord();
		request.put("name", "Ana María Torres");
		request.put("email", "ana.torres@infosgroup.com");
		request.put("level", 4);

		put(ADMIN, resourceId, request).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.name").isEqualTo("Ana María Torres")
			.jsonPath("$.email").isEqualTo("ana.torres@infosgroup.com")
			.jsonPath("$.level").isEqualTo(4)
			.jsonPath("$.assignment.project.name").isEqualTo("RT Programa de automatización")
			.jsonPath("$.situation.code").isEqualTo("ASIGNADO")
			.jsonPath("$.version").isEqualTo(1)
			.jsonPath("$.updatedBy").isEqualTo(TestSecurityConfiguration.usernameOf(ADMIN));

		assertThat(auditRows()).allSatisfy(row -> assertThat(row).containsEntry("action", "UPDATE")
			.containsEntry("actor", TestSecurityConfiguration.usernameOf(ADMIN))
			.containsKey("occurred_at"))
			.extracting(ResourceUpdateControllerTest::change)
			.containsExactlyInAnyOrder("name: Ana Torres -> Ana María Torres",
					"email: null -> ana.torres@infosgroup.com", "level: 3 -> 4");
	}

	/** CA-07: saving the record as it was changes nothing: same version and no audit rows. */
	@Test
	void savesNothingWhenNothingChanged() {
		put(ADMIN, resourceId, currentRecord()).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.version").isEqualTo(0)
			.jsonPath("$.updatedBy").doesNotExist();

		assertThat(auditRows()).isEmpty();
	}

	/**
	 * P-01: a person has one project at a time. Choosing another project moves them, the project teams
	 * change, and the audit log keeps the previous and the new client and project.
	 */
	@Test
	void movesThePersonToAnotherProject() {
		Map<String, Object> request = currentRecord();
		assignmentOf(request).put("projectId", otherProjectId);

		put(ADMIN, resourceId, request).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.assignment.client.name").isEqualTo("RT COPA")
			.jsonPath("$.assignment.project.name").isEqualTo("RT Portal de reservas");

		assertThat(teamSize(projectId)).isZero();
		assertThat(teamSize(otherProjectId)).isEqualTo(1);
		assertThat(auditRows()).extracting(ResourceUpdateControllerTest::change)
			.containsExactlyInAnyOrder("assignment.client: RT BANESCO -> RT COPA",
					"assignment.project: RT Programa de automatización -> RT Portal de reservas");
	}

	/**
	 * CA-04: two people edit the same version. The first one saves; the second gets 409 with the current
	 * version and who saved it, and their changes are not saved.
	 */
	@Test
	void rejectsAnEditOfAnOutdatedVersion() {
		Map<String, Object> first = currentRecord();
		first.put("name", "Ana Primera");
		Map<String, Object> second = currentRecord();
		second.put("name", "Ana Segunda");

		put(ADMIN, resourceId, first).expectStatus().isOk();
		put(ADMIN, resourceId, second).expectStatus()
			.isEqualTo(409)
			.expectHeader().contentType("application/problem+json")
			.expectBody()
			.jsonPath("$.code").isEqualTo("CONCURRENT_MODIFICATION")
			.jsonPath("$.title").isEqualTo("El registro cambió mientras lo editabas")
			.jsonPath("$.currentVersion").isEqualTo(1)
			.jsonPath("$.updatedBy").isEqualTo(TestSecurityConfiguration.usernameOf(ADMIN))
			.jsonPath("$.updatedAt").exists()
			.jsonPath("$.updatedFields").isEqualTo(List.of("name"));

		assertThat(jdbc.sql("SELECT full_name FROM employee WHERE id = :id").param("id", resourceId)
			.query(String.class).single()).isEqualTo("Ana Primera");
		assertThat(auditRows()).hasSize(1);
	}

	/**
	 * CA-08: an imported record keeps its "No especificado" values until a real one is chosen. Saving
	 * them as they are is rejected; with real values the record is saved and has nothing left to fix.
	 */
	@Test
	void rejectsImportedNotSpecifiedValuesUntilTheyAreChosen() {
		long imported = insertEmployee("RT-I1", "Importado", notSpecifiedWorkModeId, notSpecifiedProjectId, leaderId);
		Map<String, Object> request = recordOf(imported);
		request.put("workModeId", notSpecifiedWorkModeId);
		assignmentOf(request).put("projectId", notSpecifiedProjectId);

		put(ADMIN, imported, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
			.jsonPath("$.errors[0].field").isEqualTo("workModeId")
			.jsonPath("$.errors[0].code").isEqualTo("NOT_SPECIFIED_VALUE")
			.jsonPath("$.errors[1].field").isEqualTo("assignment.projectId")
			.jsonPath("$.errors[1].code").isEqualTo("INACTIVE_PROJECT");

		request.put("workModeId", workModeId);
		assignmentOf(request).put("projectId", projectId);
		put(ADMIN, imported, request).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.fieldsToFix").isEmpty()
			.jsonPath("$.workMode.name").isEqualTo("Virtual");
	}

	/**
	 * BD-08: Baja requires the termination date and reason, and the date cannot be before the contract
	 * start. A valid Baja leaves the person without project, and the audit log keeps the project they
	 * left.
	 */
	@Test
	void requiresTerminationOnBajaAndLeavesTheProject() {
		Map<String, Object> request = currentRecord();
		request.put("status", "BAJA");
		put(ADMIN, resourceId, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[*].field").isEqualTo(List.of("termination.date", "termination.reasonId"));

		request.put("termination", Map.of("date", HIRE_DATE.minusDays(1).toString(), "reasonId", reasonId));
		put(ADMIN, resourceId, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[0].field").isEqualTo("termination.date")
			.jsonPath("$.errors[0].code").isEqualTo("DATE_BEFORE_HIRE");

		request.put("termination", Map.of("date", TODAY.toString(), "reasonId", reasonId));
		put(ADMIN, resourceId, request).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.status.code").isEqualTo("BAJA")
			.jsonPath("$.assignment").doesNotExist()
			.jsonPath("$.situation.code").isEqualTo("SIN_PROYECTO")
			.jsonPath("$.termination.reason.name").isEqualTo("Renuncia voluntaria");

		assertThat(jdbc.sql("SELECT project_id IS NULL FROM employee WHERE id = :id").param("id", resourceId)
			.query(Boolean.class).single()).isTrue();
		assertThat(auditRows()).extracting(ResourceUpdateControllerTest::change)
			.contains("status: Activo -> Baja", "assignment.project: RT Programa de automatización -> null",
					"assignment.leader: Lorena Batista -> null", "termination.date: null -> " + TODAY);
	}

	/** Back from Baja the termination is cleared, and the person can go without project. */
	@Test
	void clearsTheTerminationWhenTheStatusChanges() {
		jdbc.sql("""
				UPDATE employee SET status_id = (SELECT id FROM resource_status WHERE code = 'BAJA'),
				       termination_date = DATE '2026-01-31', termination_reason_id = :reason, project_id = NULL
				WHERE id = :id""").param("reason", reasonId).param("id", resourceId).update();
		Map<String, Object> request = currentRecord();
		request.put("termination", Map.of("date", "2026-01-31", "reasonId", reasonId));
		request.put("status", "ACTIVO");
		request.put("assignment", null);

		put(ADMIN, resourceId, request).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.status.code").isEqualTo("ACTIVO")
			.jsonPath("$.termination").doesNotExist()
			.jsonPath("$.assignment").doesNotExist()
			.jsonPath("$.situation.code").isEqualTo("SIN_PROYECTO");
	}

	/**
	 * CA-02: every missing required field is reported at once. The NPD lead is required only with a
	 * project, and the role in the client is optional.
	 */
	@Test
	void rejectsMissingRequiredFieldsAllAtOnce() {
		Map<String, Object> request = currentRecord();
		request.put("name", " ");
		request.put("positionId", null);
		request.put("status", null);
		request.put("version", null);
		assignmentOf(request).put("leaderId", null);
		assignmentOf(request).put("clientRoleId", null);

		put(ADMIN, resourceId, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.detail").isEqualTo("No se guardó el registro. Corrige los 5 campos marcados.")
			.jsonPath("$.errors[*].field")
			.isEqualTo(List.of("name", "positionId", "status", "assignment.leaderId", "version"))
			.jsonPath("$.errors[4].message").isEqualTo("Falta la versión del registro. Recárgalo y vuelve a intentarlo.");

		Map<String, Object> withoutProject = currentRecord();
		withoutProject.put("assignment", null);
		put(ADMIN, resourceId, withoutProject).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.situation.code").isEqualTo("SIN_PROYECTO");
	}

	/**
	 * CA-02, RQ-02: the end date cannot have passed (or it is "indefinido"), and the person cannot be
	 * their own NPD lead.
	 */
	@Test
	void rejectsPastEndDateAndSelfLead() {
		Map<String, Object> request = currentRecord();
		assignmentOf(request).put("endDate", TODAY.minusDays(1).toString());
		assignmentOf(request).put("leaderId", resourceId);

		put(ADMIN, resourceId, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[0].field").isEqualTo("assignment.leaderId")
			.jsonPath("$.errors[0].code").isEqualTo("SELF_LEADER")
			.jsonPath("$.errors[1].field").isEqualTo("assignment.endDate")
			.jsonPath("$.errors[1].code").isEqualTo("DATE_IN_PAST");

		Map<String, Object> indefinite = currentRecord();
		assignmentOf(indefinite).put("endDate", null);
		assignmentOf(indefinite).put("indefinite", true);
		put(ADMIN, resourceId, indefinite).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.assignment.indefinite").isEqualTo(true);
		assertThat(auditRows()).extracting(ResourceUpdateControllerTest::change)
			.containsExactly("assignment.endDate: " + END_DATE + " -> Indefinido");
	}

	/**
	 * The employee number and e-mail stay unique: the resource keeps its own, but cannot take another
	 * resource's.
	 */
	@Test
	void rejectsTheNumberOrEmailOfAnotherResource() {
		jdbc.sql("UPDATE employee SET email = 'lorena@infosgroup.com' WHERE id = :id").param("id", leaderId).update();
		Map<String, Object> request = currentRecord();
		request.put("employeeNumber", "rt-l1");

		put(ADMIN, resourceId, request).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("EMPLOYEE_NUMBER_TAKEN")
			.jsonPath("$.existingResourceId").isEqualTo(leaderId);

		request.put("email", "Lorena@infosgroup.com");
		put(ADMIN, resourceId, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[*].code").isEqualTo(List.of("EMPLOYEE_NUMBER_TAKEN", "EMAIL_TAKEN"));
	}

	/**
	 * CA-05: only an Operations administrator edits; the other roles get 403 and nothing changes.
	 *
	 * @param role a read-only role
	 */
	@ParameterizedTest
	@ValueSource(strings = { "guest", "npd-leader", "customer-success" })
	void deniesEditToOtherRoles(String role) {
		Map<String, Object> request = currentRecord();
		request.put("name", "Cambio no permitido");

		put(role, resourceId, request).expectStatus()
			.isForbidden()
			.expectBody()
			.jsonPath("$.code").isEqualTo("FORBIDDEN");
		assertThat(jdbc.sql("SELECT version FROM employee WHERE id = :id").param("id", resourceId)
			.query(Integer.class).single()).isZero();
	}

	/** An edit needs a valid token and an existing resource. */
	@Test
	void requiresTokenAndExistingResource() {
		client.put().uri(RESOURCES + resourceId).contentType(APPLICATION_JSON).body(currentRecord()).exchange()
			.expectStatus().isUnauthorized();

		put(ADMIN, 999999, currentRecord()).expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.code").isEqualTo("NOT_FOUND");
	}

	private RestTestClient.ResponseSpec put(String token, long id, Object body) {
		return client.put()
			.uri(RESOURCES + id)
			.header(AUTHORIZATION, "Bearer " + token)
			.contentType(APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	/** The edit form of the resource as it is stored, as the frontend fills it from its record. */
	private Map<String, Object> currentRecord() {
		return recordOf(resourceId);
	}

	private Map<String, Object> recordOf(long id) {
		Map<String, Object> stored = jdbc.sql("""
				SELECT code, full_name, email, level, work_modality_id, hire_date, on_probation, version, project_id,
				       leader_id
				FROM employee WHERE id = :id""").param("id", id).query().singleRow();
		Map<String, Object> request = new HashMap<>();
		request.put("employeeNumber", stored.get("code"));
		request.put("name", stored.get("full_name"));
		request.put("email", stored.get("email"));
		request.put("positionId", positionId);
		request.put("level", stored.get("level"));
		request.put("workModeId", stored.get("work_modality_id"));
		request.put("genderId", genderId);
		request.put("hireDate", stored.get("hire_date").toString());
		request.put("onProbation", stored.get("on_probation"));
		request.put("status", "ACTIVO");
		request.put("version", stored.get("version"));
		Map<String, Object> assignment = new HashMap<>();
		assignment.put("projectId", stored.get("project_id"));
		assignment.put("leaderId", stored.get("leader_id"));
		assignment.put("clientRoleId", clientRoleId);
		assignment.put("equipmentId", equipmentId);
		assignment.put("endDate", END_DATE.toString());
		assignment.put("indefinite", false);
		request.put("assignment", assignment);
		return request;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> assignmentOf(Map<String, Object> request) {
		return (Map<String, Object>) request.get("assignment");
	}

	private List<Map<String, Object>> auditRows() {
		return jdbc.sql("""
				SELECT action, field, old_value, new_value, actor, occurred_at FROM audit_log
				WHERE entity_type = 'employee' AND entity_id = :id ORDER BY id""")
			.param("id", resourceId)
			.query()
			.listOfRows();
	}

	private static String change(Map<String, Object> row) {
		return row.get("field") + ": " + row.get("old_value") + " -> " + row.get("new_value");
	}

	private long teamSize(long project) {
		return jdbc.sql("SELECT team_size FROM project_summary_v WHERE project_id = :id")
			.param("id", project)
			.query(Long.class)
			.single();
	}

	private long insert(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private long insertProject(String clientName, String projectName) {
		long clientId = jdbc.sql("INSERT INTO client (name) VALUES (:name) RETURNING id")
			.param("name", clientName)
			.query(Long.class)
			.single();
		return jdbc.sql("INSERT INTO project (client_id, name) VALUES (:client, :name) RETURNING id")
			.param("client", clientId)
			.param("name", projectName)
			.query(Long.class)
			.single();
	}

	private short catalogId(String table, String code) {
		return jdbc.sql("SELECT id FROM " + table + " WHERE code = :code").param("code", code).query(Short.class).single();
	}

	private long insertEmployee(String code, String name, short workModality, Long project, Long leader) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, level, hire_date, status_id,
				                      work_modality_id, project_id, leader_id, client_role_id,
				                      equipment_provider_id, project_end_date)
				VALUES (:code, :name, :gender, :position, 3, :hireDate,
				        (SELECT id FROM resource_status WHERE code = 'ACTIVO'), :workModality, :project, :leader,
				        :clientRole, :equipment, :endDate)
				RETURNING id""")
			.param("code", code)
			.param("name", name)
			.param("gender", genderId)
			.param("position", positionId)
			.param("hireDate", HIRE_DATE)
			.param("workModality", workModality)
			.param("project", project, Types.BIGINT)
			.param("leader", leader, Types.BIGINT)
			.param("clientRole", project == null ? null : clientRoleId, Types.BIGINT)
			.param("equipment", project == null ? null : equipmentId, Types.SMALLINT)
			.param("endDate", project == null ? null : END_DATE, Types.DATE)
			.query(Long.class)
			.single();
	}

}
