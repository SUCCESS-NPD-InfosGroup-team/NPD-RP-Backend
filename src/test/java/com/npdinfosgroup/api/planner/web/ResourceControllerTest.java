package com.npdinfosgroup.api.planner.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
 * Registering a resource through the API (HU-01), end to end: security, validation, the saved record,
 * its situation, the project summary and the audit log.
 * <p>
 * The requests run in the server's own transactions, so each test cleans up the rows it created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class ResourceControllerTest {

	private static final String ADMIN = "operations-administrator";

	private static final String RESOURCES = "/api/v1/resources";

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	private static final LocalDate HIRE_DATE = TODAY.minusDays(10);

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	private long positionId;

	private long jobTitleId;

	private long clientRoleId;

	private long projectId;

	private long inactiveProjectId;

	private long leaderId;

	private long terminatedLeaderId;

	private short genderId;

	private short workModeId;

	private short notSpecifiedWorkModeId;

	private short equipmentId;

	private short reasonId;

	/**
	 * A client with an active and an inactive project, an active NPD lead and one on Baja.
	 */
	@BeforeEach
	void createFixture() {
		positionId = insert("INSERT INTO job_position (name) VALUES ('RT Analista de pruebas') RETURNING id");
		jobTitleId = insert("INSERT INTO job_title (name) VALUES ('RT Analista') RETURNING id");
		clientRoleId = insert("INSERT INTO client_role (name) VALUES ('RT QA Funcional') RETURNING id");
		long clientId = insert("INSERT INTO client (name) VALUES ('RT BANESCO') RETURNING id");
		projectId = jdbc.sql("""
				INSERT INTO project (client_id, name, contract_end_date)
				VALUES (:client, 'RT Programa de automatización', DATE '2031-12-31') RETURNING id""")
			.param("client", clientId)
			.query(Long.class)
			.single();
		inactiveProjectId = jdbc.sql("INSERT INTO project (client_id, name, active) VALUES (:client, 'RT Cerrado', false) RETURNING id")
			.param("client", clientId)
			.query(Long.class)
			.single();
		genderId = catalogId("gender", "F");
		workModeId = catalogId("work_modality", "VIRTUAL");
		notSpecifiedWorkModeId = catalogId("work_modality", "NO_ESPECIFICADO");
		equipmentId = catalogId("equipment_provider", "CLIENTE");
		reasonId = catalogId("termination_reason", "RENUNCIA_VOLUNTARIA");
		leaderId = insertEmployee("RT-L1", "Lorena Batista", "ACTIVO", workModeId, null);
		terminatedLeaderId = insertEmployee("RT-L2", "Pedro Salida", "BAJA", workModeId, null);
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
		jdbc.sql("DELETE FROM job_title WHERE name LIKE 'RT %'").update();
		jdbc.sql("DELETE FROM client_role WHERE name LIKE 'RT %'").update();
	}

	/**
	 * CA-01, CA-06, CA-08, CA-12: a resource with a project is created with its project data, its
	 * calculated situation and version 0, can be read right away at its location, raises the project's
	 * team size and leaves its initial values in the audit log.
	 */
	@Test
	void registersResourceWithProject() {
		Map<String, Object> request = validRequest("1102");
		request.put("onProbation", true);
		request.put("assignment", assignment());

		URI location = post(ADMIN, request).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.employeeNumber").isEqualTo("1102")
			.jsonPath("$.name").isEqualTo("Laura Gómez")
			.jsonPath("$.workMode.name").isEqualTo("Virtual")
			.jsonPath("$.hireDate").isEqualTo(HIRE_DATE.toString())
			.jsonPath("$.onProbation").isEqualTo(true)
			.jsonPath("$.probationEndDate").isEqualTo(HIRE_DATE.plusDays(90).toString())
			.jsonPath("$.status.code").isEqualTo("ACTIVO")
			.jsonPath("$.situation.code").isEqualTo("ASIGNADO")
			.jsonPath("$.situation.label").isEqualTo("Asignado")
			.jsonPath("$.assignment.client.name").isEqualTo("RT BANESCO")
			.jsonPath("$.assignment.project.id").value(Number.class, id(projectId))
			.jsonPath("$.assignment.project.contractEndDate").isEqualTo("2031-12-31")
			.jsonPath("$.assignment.leader.name").isEqualTo("Lorena Batista")
			.jsonPath("$.assignment.clientRole.name").isEqualTo("RT QA Funcional")
			.jsonPath("$.assignment.equipment.name").isEqualTo("Cliente")
			.jsonPath("$.assignment.endDate").isEqualTo(TODAY.plusMonths(3).toString())
			.jsonPath("$.assignment.indefinite").isEqualTo(false)
			.jsonPath("$.termination").doesNotExist()
			.jsonPath("$.fieldsToFix").isEmpty()
			.jsonPath("$.version").isEqualTo(0)
			.jsonPath("$.updatedBy").isEqualTo(TestSecurityConfiguration.usernameOf(ADMIN))
			.returnResult()
			.getResponseHeaders()
			.getLocation();

		long id = jdbc.sql("SELECT id FROM employee WHERE code = '1102'").query(Long.class).single();
		assertThat(location).hasPath(RESOURCES + "/" + id);
		client.get().uri(location).header(AUTHORIZATION, "Bearer guest").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.name").isEqualTo("Laura Gómez")
			.jsonPath("$.updatedBy").isEqualTo(TestSecurityConfiguration.usernameOf(ADMIN));

		assertThat(jdbc.sql("SELECT team_size FROM project_summary_v WHERE project_id = :id")
			.param("id", projectId).query(Long.class).single()).isEqualTo(1);
		List<Map<String, Object>> audit = jdbc.sql("""
				SELECT action, field, old_value, new_value, actor FROM audit_log
				WHERE entity_type = 'employee' AND entity_id = :id""").param("id", id).query().listOfRows();
		assertThat(audit).allSatisfy(row -> assertThat(row).containsEntry("action", "CREATE")
			.containsEntry("old_value", null)
			.containsEntry("actor", TestSecurityConfiguration.usernameOf(ADMIN)));
		assertThat(audit).extracting(row -> row.get("field") + "=" + row.get("new_value"))
			.contains("employeeNumber=1102", "workMode=Virtual", "onProbation=Sí", "status=Activo",
					"assignment.project=RT Programa de automatización", "assignment.leader=Lorena Batista",
					"assignment.endDate=" + TODAY.plusMonths(3))
			.noneMatch(value -> value.toString().startsWith("talentReview="));
	}

	/**
	 * CA-01, CA-02: "Sin proyecto por ahora" asks for no project data and leaves the person without
	 * project; "indefinido" is stored as no end date.
	 */
	@Test
	void registersResourceWithoutProjectAndWithIndefiniteEnd() {
		Map<String, Object> withoutProject = validRequest("1103");
		withoutProject.put("businessUnitId", catalogId("business_unit", "AE"));
		withoutProject.put("talentReviewId", catalogId("talent_review", "DILEMA"));
		post(ADMIN, withoutProject).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.situation.code").isEqualTo("SIN_PROYECTO")
			.jsonPath("$.onProbation").isEqualTo(false)
			.jsonPath("$.businessUnit.name").isEqualTo("AE")
			.jsonPath("$.talentReview.name").isEqualTo("Dilema")
			.jsonPath("$.assignment").doesNotExist();

		Map<String, Object> indefinite = validRequest("1104");
		indefinite.put("email", null);
		Map<String, Object> assignment = assignment();
		assignment.put("endDate", null);
		assignment.put("indefinite", true);
		indefinite.put("assignment", assignment);
		post(ADMIN, indefinite).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.assignment.endDate").doesNotExist()
			.jsonPath("$.assignment.indefinite").isEqualTo(true);
		assertThat(jdbc.sql("SELECT new_value FROM audit_log WHERE field = 'assignment.endDate'")
			.query(String.class).single()).isEqualTo("Indefinido");
	}

	/**
	 * CA-02, CA-09: every missing required field is reported at once, in form order, and nothing is
	 * saved.
	 */
	@Test
	void rejectsMissingRequiredFieldsAllAtOnce() {
		post(ADMIN, Map.of()).expectStatus()
			.isBadRequest()
			.expectHeader().contentType("application/problem+json")
			.expectBody()
			.jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
			.jsonPath("$.detail").isEqualTo("No se guardó el registro. Corrige los 8 campos marcados.")
			.jsonPath("$.errors[*].field")
			.isEqualTo(List.of("employeeNumber", "name", "positionId", "workModeId", "genderId", "hireDate", "onProbation",
					"status"))
			.jsonPath("$.errors[*].code").value(List.class, codes -> assertThat(codes).containsOnly("REQUIRED"))
			.jsonPath("$.errors[3].message").isEqualTo("Selecciona la modalidad laboral.");
		assertThat(employeeCount()).isEqualTo(2);
	}

	/**
	 * CA-01, CA-02: with a project, the project, the NPD lead and the end date (or "indefinido") are
	 * required.
	 */
	@Test
	void requiresProjectLeaderAndEndDateWhenThereIsAProject() {
		Map<String, Object> request = validRequest("1105");
		request.put("assignment", Map.of());

		post(ADMIN, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[*].field")
			.isEqualTo(List.of("assignment.projectId", "assignment.leaderId", "assignment.endDate"))
			.jsonPath("$.errors[1].message").isEqualTo("Selecciona el líder NPD.")
			.jsonPath("$.errors[2].message").isEqualTo("Indica la fecha final o marca indefinido.");
	}

	/**
	 * CA-03: an end date that already passed is rejected with its message, and so is an end date
	 * together with "indefinido".
	 */
	@Test
	void rejectsPastOrConflictingEndDate() {
		Map<String, Object> past = validRequest("1106");
		Map<String, Object> pastAssignment = assignment();
		pastAssignment.put("endDate", TODAY.minusDays(1).toString());
		past.put("assignment", pastAssignment);
		post(ADMIN, past).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[0].field").isEqualTo("assignment.endDate")
			.jsonPath("$.errors[0].code").isEqualTo("DATE_IN_PAST")
			.jsonPath("$.errors[0].message").isEqualTo("Ya pasó. Usa una fecha futura o marca indefinido.");

		Map<String, Object> both = validRequest("1106");
		Map<String, Object> bothAssignment = assignment();
		bothAssignment.put("indefinite", true);
		both.put("assignment", bothAssignment);
		post(ADMIN, both).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[0].code").isEqualTo("CONFLICTING_VALUES");
	}

	/**
	 * CA-03, CA-13: Baja requires the termination reason and date, and the date cannot be earlier than
	 * the contract start.
	 */
	@Test
	void requiresValidTerminationOnBaja() {
		Map<String, Object> missing = validRequest("1107");
		missing.put("status", "BAJA");
		post(ADMIN, missing).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[*].field").isEqualTo(List.of("termination.date", "termination.reasonId"));

		Map<String, Object> early = validRequest("1107");
		early.put("status", "BAJA");
		early.put("termination", Map.of("date", HIRE_DATE.minusDays(1).toString(), "reasonId", reasonId));
		post(ADMIN, early).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[0].field").isEqualTo("termination.date")
			.jsonPath("$.errors[0].code").isEqualTo("DATE_BEFORE_HIRE");
	}

	/**
	 * CA-13: on Baja the person is saved without project, even if a project was sent.
	 */
	@Test
	void savesTerminatedResourceWithoutProject() {
		Map<String, Object> request = validRequest("1108");
		request.put("status", "BAJA");
		request.put("termination", Map.of("date", TODAY.toString(), "reasonId", reasonId));
		request.put("assignment", assignment());

		post(ADMIN, request).expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.status.code").isEqualTo("BAJA")
			.jsonPath("$.situation.code").isEqualTo("SIN_PROYECTO")
			.jsonPath("$.assignment").doesNotExist()
			.jsonPath("$.termination.date").isEqualTo(TODAY.toString())
			.jsonPath("$.termination.reason.name").isEqualTo("Renuncia voluntaria");
	}

	/**
	 * CA-04: a taken employee number alone answers 409 with a link to the existing record and creates
	 * nothing.
	 */
	@Test
	void rejectsDuplicateEmployeeNumber() {
		post(ADMIN, validRequest("1045")).expectStatus().isCreated();
		long existingId = jdbc.sql("SELECT id FROM employee WHERE code = '1045'").query(Long.class).single();

		Map<String, Object> duplicate = validRequest("1045");
		duplicate.put("name", "Otra Persona");
		duplicate.put("email", null);
		post(ADMIN, duplicate).expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.code").isEqualTo("EMPLOYEE_NUMBER_TAKEN")
			.jsonPath("$.detail").isEqualTo("El N° 1045 ya está registrado (Laura Gómez).")
			.jsonPath("$.existingResourceId").value(Number.class, id(existingId))
			.jsonPath("$.existingResourceName").isEqualTo("Laura Gómez");
		assertThat(employeeCount()).isEqualTo(3);
	}

	/**
	 * CA-09: a taken employee number, a missing NPD lead and a past end date are listed together in the
	 * error summary.
	 */
	@Test
	void listsDuplicateNumberWithTheOtherErrors() {
		post(ADMIN, validRequest("1045")).expectStatus().isCreated();

		Map<String, Object> request = validRequest(" 1045 ");
		request.put("email", "otra@infosgroup.com");
		Map<String, Object> assignment = assignment();
		assignment.put("leaderId", null);
		assignment.put("endDate", TODAY.minusDays(30).toString());
		request.put("assignment", assignment);

		post(ADMIN, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.detail").isEqualTo("No se guardó el registro. Corrige los 3 campos marcados.")
			.jsonPath("$.errors[*].code")
			.isEqualTo(List.of("EMPLOYEE_NUMBER_TAKEN", "REQUIRED", "DATE_IN_PAST"))
			.jsonPath("$.errors[*].field")
			.isEqualTo(List.of("employeeNumber", "assignment.leaderId", "assignment.endDate"))
			.jsonPath("$.errors[0].message").isEqualTo("El N° 1045 ya está registrado (Laura Gómez).")
			.jsonPath("$.errors[0].existingResourceName").isEqualTo("Laura Gómez");
	}

	/**
	 * Values that cannot be chosen: an inactive "No especificado", an inactive project, a lead on Baja,
	 * unknown ids and status, a taken e-mail and badly formed values.
	 */
	@Test
	void rejectsValuesThatCannotBeChosen() {
		post(ADMIN, validRequest("1109")).expectStatus().isCreated();

		Map<String, Object> request = validRequest("1110");
		request.put("workModeId", notSpecifiedWorkModeId);
		request.put("genderId", 999);
		request.put("level", 0);
		Map<String, Object> assignment = assignment();
		assignment.put("projectId", inactiveProjectId);
		assignment.put("leaderId", terminatedLeaderId);
		assignment.put("clientContactEmail", "no-es-correo");
		request.put("assignment", assignment);

		post(ADMIN, request).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[*].field")
			.isEqualTo(List.of("email", "level", "workModeId", "genderId", "assignment.projectId",
					"assignment.leaderId", "assignment.clientContactEmail"))
			.jsonPath("$.errors[*].code")
			.isEqualTo(List.of("EMAIL_TAKEN", "INVALID_FORMAT", "NOT_SPECIFIED_VALUE", "UNKNOWN_ID",
					"INACTIVE_PROJECT", "INACTIVE_LEADER", "INVALID_FORMAT"));

		Map<String, Object> unknown = validRequest("1111");
		unknown.put("email", null);
		unknown.put("status", "JUBILADO");
		Map<String, Object> unknownAssignment = assignment();
		unknownAssignment.put("projectId", 999_999);
		unknownAssignment.put("leaderId", 999_999);
		unknown.put("assignment", unknownAssignment);
		post(ADMIN, unknown).expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.errors[*].code").isEqualTo(List.of("UNKNOWN_VALUE", "UNKNOWN_ID", "UNKNOWN_ID"));
	}

	/**
	 * CA-05: only an Operations administrator registers; the other roles get 403 and nothing is
	 * created.
	 *
	 * @param role a read-only role
	 */
	@ParameterizedTest
	@ValueSource(strings = { "guest", "npd-leader", "customer-success" })
	void deniesRegistrationToOtherRoles(String role) {
		post(role, validRequest("1112")).expectStatus()
			.isForbidden()
			.expectHeader().contentType("application/problem+json")
			.expectBody()
			.jsonPath("$.code").isEqualTo("FORBIDDEN")
			.jsonPath("$.instance").isEqualTo(RESOURCES);
		assertThat(employeeCount()).isEqualTo(2);
	}

	/** Without a valid token the API answers 401. */
	@Test
	void requiresValidToken() {
		client.post().uri(RESOURCES).contentType(APPLICATION_JSON).body(validRequest("1113")).exchange()
			.expectStatus().isUnauthorized()
			.expectBody()
			.jsonPath("$.code").isEqualTo("UNAUTHORIZED");
		post(TestSecurityConfiguration.INVALID_TOKEN, validRequest("1113")).expectStatus().isUnauthorized();
	}

	/** A record that does not exist answers 404, and a malformed body 400. */
	@Test
	void answersNotFoundAndMalformedBody() {
		client.get().uri(RESOURCES + "/999999").header(AUTHORIZATION, "Bearer guest").exchange()
			.expectStatus().isNotFound()
			.expectBody()
			.jsonPath("$.code").isEqualTo("NOT_FOUND")
			.jsonPath("$.detail").isEqualTo("No existe el registro 999999.");

		client.post().uri(RESOURCES).header(AUTHORIZATION, "Bearer " + ADMIN).contentType(APPLICATION_JSON)
			.body("{ not json").exchange()
			.expectStatus().isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("INVALID_REQUEST");
	}

	/**
	 * An imported record that still has "No especificado" values lists them in {@code fieldsToFix} and
	 * has no API author yet.
	 */
	@Test
	void flagsImportedNotSpecifiedValues() {
		long imported = insertEmployee("RT-I1", "Importado", "ACTIVO", notSpecifiedWorkModeId,
				jdbc.sql("SELECT id FROM project WHERE name = 'No especificado'").query(Long.class).single());

		client.get().uri(RESOURCES + "/" + imported).header(AUTHORIZATION, "Bearer npd-leader").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.fieldsToFix").isEqualTo(List.of("workModeId", "assignment.projectId"))
			.jsonPath("$.assignment.indefinite").isEqualTo(true)
			.jsonPath("$.updatedBy").doesNotExist();
	}

	/**
	 * The "Líder NPD" selector offers who leads someone today, except those on Baja; whoever leads
	 * nobody is not offered.
	 */
	@Test
	void listsNpdLeads() {
		long led = insertEmployee("RT-C1", "Colaborador uno", "ACTIVO", workModeId, null);
		long ledByTerminated = insertEmployee("RT-C2", "Colaborador dos", "ACTIVO", workModeId, null);
		jdbc.sql("UPDATE employee SET leader_id = :leader WHERE id = :id").param("leader", leaderId).param("id", led)
			.update();
		jdbc.sql("UPDATE employee SET leader_id = :leader WHERE id = :id")
			.param("leader", terminatedLeaderId)
			.param("id", ledByTerminated)
			.update();

		client.get().uri(RESOURCES + "/npd-leads").header(AUTHORIZATION, "Bearer guest").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.items[*].name").isEqualTo(List.of("Lorena Batista"))
			.jsonPath("$.items[0].id").value(Number.class, id(leaderId));

		client.get().uri(RESOURCES + "/npd-leads").exchange().expectStatus().isUnauthorized();
	}

	private RestTestClient.ResponseSpec post(String token, Object body) {
		return client.post()
			.uri(RESOURCES)
			.header(AUTHORIZATION, "Bearer " + token)
			.contentType(APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	private Map<String, Object> validRequest(String employeeNumber) {
		Map<String, Object> request = new HashMap<>();
		request.put("employeeNumber", employeeNumber);
		request.put("name", "Laura Gómez");
		request.put("email", "laura.gomez@infosgroup.com");
		request.put("jobTitleId", jobTitleId);
		request.put("positionId", positionId);
		request.put("level", 3);
		request.put("workModeId", workModeId);
		request.put("genderId", genderId);
		request.put("hireDate", HIRE_DATE.toString());
		request.put("onProbation", false);
		request.put("status", "ACTIVO");
		return request;
	}

	private Map<String, Object> assignment() {
		Map<String, Object> assignment = new HashMap<>();
		assignment.put("projectId", projectId);
		assignment.put("leaderId", leaderId);
		assignment.put("clientRoleId", clientRoleId);
		assignment.put("equipmentId", equipmentId);
		assignment.put("endDate", TODAY.plusMonths(3).toString());
		assignment.put("indefinite", false);
		assignment.put("clientContactEmail", "mmedina@banesco.com");
		return assignment;
	}

	private long insert(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private short catalogId(String table, String code) {
		return jdbc.sql("SELECT id FROM " + table + " WHERE code = :code").param("code", code).query(Short.class).single();
	}

	private long insertEmployee(String code, String name, String status, short workModality, Long project) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, hire_date, status_id,
				                      work_modality_id, project_id)
				VALUES (:code, :name, :gender, :position, DATE '2024-01-15',
				        (SELECT id FROM resource_status WHERE code = :status), :workModality, :project)
				RETURNING id""")
			.param("code", code)
			.param("name", name)
			.param("gender", genderId)
			.param("position", positionId)
			.param("status", status)
			.param("workModality", workModality)
			.param("project", project, Types.BIGINT)
			.query(Long.class)
			.single();
	}

	private long employeeCount() {
		return jdbc.sql("SELECT count(*) FROM employee").query(Long.class).single();
	}

	private static Consumer<Number> id(long expected) {
		return actual -> assertThat(actual.longValue()).isEqualTo(expected);
	}

}
