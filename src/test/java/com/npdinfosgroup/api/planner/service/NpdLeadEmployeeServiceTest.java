package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.planner.dto.request.NewNpdLeadCommand;
import com.npdinfosgroup.api.planner.dto.request.NpdLeadEmployeeRequest;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The employee side of an NPD lead user against PostgreSQL: registering the record, Baja by role
 * change, back to Activo and renaming, each audited as a resource edit.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NpdLeadEmployeeServiceTest {

	private static final String ACTOR = "admin@example.com";

	@Autowired
	private NpdLeadEmployeeService service;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private Clock clock;

	private long positionId;

	private short genderId;

	private short workModeId;

	/** A position of the test and the ids of the seeded catalogs. */
	@BeforeEach
	void createCatalogs() {
		positionId = jdbc.sql("INSERT INTO job_position (name) VALUES ('NL Analista') RETURNING id").query(Long.class).single();
		genderId = jdbc.sql("SELECT id FROM gender WHERE code = 'F'").query(Short.class).single();
		workModeId = jdbc.sql("SELECT id FROM work_modality WHERE code = 'VIRTUAL'").query(Short.class).single();
	}

	/** Deletes the rows of the test. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log WHERE entity_type = 'employee'").update();
		jdbc.sql("UPDATE employee SET leader_id = NULL WHERE code LIKE 'NL-%'").update();
		jdbc.sql("DELETE FROM employee WHERE code LIKE 'NL-%'").update();
		jdbc.sql("DELETE FROM project WHERE name = 'NL Proyecto'").update();
		jdbc.sql("DELETE FROM client WHERE name = 'NL CLIENTE'").update();
		jdbc.sql("DELETE FROM job_position WHERE name = 'NL Analista'").update();
	}

	/**
	 * The record is created with the user's name and email, marked as NPD lead, without project or lead,
	 * and its creation is audited.
	 */
	@Test
	void registersTheRecordOfANewLead() {
		long id = service.register(command("NL-1", "LICENCIA"));

		assertThat(jdbc.sql("""
				SELECT e.full_name, e.email, e.is_npd_lead, s.code, e.project_id, e.leader_id, e.on_probation
				FROM employee e JOIN resource_status s ON s.id = e.status_id WHERE e.id = :id""")
			.param("id", id)
			.query((rs, row) -> List.of(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getString(4),
					String.valueOf(rs.getObject(5)), String.valueOf(rs.getObject(6)), rs.getBoolean(7)))
			.single()).containsExactly("Kathya Solís", "kathya@example.com", true, "LICENCIA", "null", "null", false);
		assertThat(auditFields(id)).contains("employeeNumber", "name", "email", "status");
	}

	/** The field errors come together, named after the {@code employee} object, and Baja is refused. */
	@Test
	void reportsTheFieldErrorsOfTheRecord() {
		NpdLeadEmployeeRequest employee = new NpdLeadEmployeeRequest(" ", null, positionId, workModeId,
				LocalDate.of(2026, 3, 3), "BAJA");

		assertThatThrownBy(() -> service.register(new NewNpdLeadCommand(employee, "Kathya Solís",
				"kathya@example.com", ACTOR)))
			.isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getProblems())
				.extracting(FieldProblem::field, FieldProblem::code)
				.containsExactlyInAnyOrder(tuple("employee.status", FieldErrorCode.UNKNOWN_VALUE),
						tuple("employee.employeeNumber", FieldErrorCode.REQUIRED),
						tuple("employee.genderId", FieldErrorCode.REQUIRED)));
	}

	/** Baja alone is reported as well, when the other fields are valid. */
	@Test
	void refusesANewLeadOnBaja() {
		assertThatThrownBy(() -> service.register(command("NL-1", "BAJA")))
			.isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getProblems())
				.extracting(FieldProblem::field)
				.containsExactly("employee.status"));
	}

	/**
	 * CA-11: Baja today by "Cambio de rol", out of their project, still an NPD lead; the collaborators
	 * keep them as lead. Back as NPD lead (CA-10): Activo, termination cleared, still without project.
	 */
	@Test
	void leavesTheRoleAndComesBack() {
		long clientId = jdbc.sql("INSERT INTO client (name) VALUES ('NL CLIENTE') RETURNING id").query(Long.class).single();
		long projectId = jdbc.sql("INSERT INTO project (client_id, name) VALUES (:client, 'NL Proyecto') RETURNING id")
			.param("client", clientId)
			.query(Long.class)
			.single();
		long boss = insertEmployee("NL-0", null, null);
		long lead = insertEmployee("NL-1", projectId, boss);
		long collaborator = insertEmployee("NL-2", projectId, lead);

		service.leaveRole(lead, ACTOR);

		assertThat(state(lead)).containsExactly("BAJA", LocalDate.now(clock).toString(), "CAMBIO_DE_ROL", "null", "true");
		assertThat(jdbc.sql("SELECT leader_id FROM employee WHERE id = :id").param("id", collaborator)
			.query(Long.class).single()).isEqualTo(lead);
		assertThat(auditFields(lead)).contains("status", "termination.date", "termination.reason",
				"assignment.project");

		service.returnToRole(lead, ACTOR);

		assertThat(state(lead)).containsExactly("ACTIVO", "null", "null", "null", "true");
	}

	/** Only a Baja by role change comes back: any other Baja is not linkable. */
	@Test
	void doesNotBringBackAnotherBaja() {
		long lead = insertEmployee("NL-1", null, null);
		jdbc.sql("""
				UPDATE employee SET status_id = (SELECT id FROM resource_status WHERE code = 'BAJA'),
				       termination_date = DATE '2026-05-01',
				       termination_reason_id = (SELECT id FROM termination_reason WHERE code <> 'CAMBIO_DE_ROL' LIMIT 1)
				WHERE id = :id""").param("id", lead).update();

		assertThatThrownBy(() -> service.returnToRole(lead, ACTOR))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo(ErrorCode.EMPLOYEE_NOT_LINKABLE);
				assertThat(ex.getProperties()).containsEntry("reason", "TERMINATED");
			});
		assertThat(state(lead).getFirst()).isEqualTo("BAJA");
	}

	/** A record that is not on Baja keeps its status and is marked as NPD lead. */
	@Test
	void keepsTheStatusOfARecordThatIsNotOnBaja() {
		long lead = insertEmployee("NL-1", null, null);
		jdbc.sql("UPDATE employee SET is_npd_lead = false WHERE id = :id").param("id", lead).update();

		service.returnToRole(lead, ACTOR);

		assertThat(state(lead)).containsExactly("ACTIVO", "null", "null", "null", "true");
		assertThat(auditFields(lead)).isEmpty();
	}

	/** HU-11 CA-04: the record gets the user's new name, audited. */
	@Test
	void renamesTheRecord() {
		long lead = insertEmployee("NL-1", null, null);

		service.rename(lead, "Kathya Solís Mora", ACTOR);

		assertThat(jdbc.sql("SELECT full_name FROM employee WHERE id = :id").param("id", lead).query(String.class)
			.single()).isEqualTo("Kathya Solís Mora");
		assertThat(jdbc.sql("""
				SELECT old_value || ' -> ' || new_value || ' by ' || actor FROM audit_log
				WHERE entity_type = 'employee' AND entity_id = :id AND field = 'name'""")
			.param("id", lead)
			.query(String.class)
			.single()).isEqualTo("NL-1 -> Kathya Solís Mora by " + ACTOR);
	}

	/** An unknown record is a 404. */
	@Test
	void reportsAnUnknownRecord() {
		assertThatThrownBy(() -> service.rename(999_999L, "Ana", ACTOR)).isInstanceOf(NotFoundException.class);
	}

	/** The summaries of no records are read without a query. */
	@Test
	void summarizesNothing() {
		assertThat(service.summaries(List.of())).isEqualTo(Map.of());
	}

	private NewNpdLeadCommand command(String number, String status) {
		return new NewNpdLeadCommand(new NpdLeadEmployeeRequest(number, genderId, positionId, workModeId,
				LocalDate.of(2026, 3, 3), status), "Kathya Solís", "kathya@example.com", ACTOR);
	}

	private long insertEmployee(String code, Long projectId, Long leaderId) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, work_modality_id, hire_date,
				                      status_id, project_id, leader_id, is_npd_lead)
				VALUES (:code, :code, :gender, :position, :workMode, DATE '2024-01-15',
				        (SELECT id FROM resource_status WHERE code = 'ACTIVO'), :project, :leader, true)
				RETURNING id""")
			.param("code", code)
			.param("gender", genderId)
			.param("position", positionId)
			.param("workMode", workModeId)
			.param("project", projectId, Types.BIGINT)
			.param("leader", leaderId, Types.BIGINT)
			.query(Long.class)
			.single();
	}

	/** Status, termination date and reason, project and NPD lead mark of a record. */
	private List<String> state(long id) {
		return jdbc.sql("""
				SELECT s.code, e.termination_date, r.code, e.project_id, e.is_npd_lead
				FROM employee e JOIN resource_status s ON s.id = e.status_id
				LEFT JOIN termination_reason r ON r.id = e.termination_reason_id WHERE e.id = :id""")
			.param("id", id)
			.query((rs, row) -> List.of(rs.getString(1), String.valueOf(rs.getObject(2)), String.valueOf(rs.getString(3)),
					String.valueOf(rs.getObject(4)), String.valueOf(rs.getBoolean(5))))
			.single();
	}

	private List<String> auditFields(long id) {
		return jdbc.sql("SELECT field FROM audit_log WHERE entity_type = 'employee' AND entity_id = :id")
			.param("id", id)
			.query(String.class)
			.list();
	}

}
