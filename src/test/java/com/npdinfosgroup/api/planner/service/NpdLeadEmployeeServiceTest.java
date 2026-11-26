package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.planner.dto.request.NewNpdLeadCommand;
import com.npdinfosgroup.api.planner.dto.request.NpdLeadEmployeeRequest;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The employee side of an NPD lead user against PostgreSQL: registering the record, audited as a new
 * resource.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NpdLeadEmployeeServiceTest {

	private static final String ACTOR = "admin@example.com";

	@Autowired
	private NpdLeadEmployeeService service;

	@Autowired
	private JdbcClient jdbc;

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
		jdbc.sql("DELETE FROM employee WHERE code LIKE 'NL-%'").update();
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

	/** The summaries of no records are read without a query. */
	@Test
	void summarizesNothing() {
		assertThat(service.summaries(List.of())).isEqualTo(Map.of());
	}

	private NewNpdLeadCommand command(String number, String status) {
		return new NewNpdLeadCommand(new NpdLeadEmployeeRequest(number, genderId, positionId, workModeId,
				LocalDate.of(2026, 3, 3), status), "Kathya Solís", "kathya@example.com", ACTOR);
	}

	private List<String> auditFields(long id) {
		return jdbc.sql("SELECT field FROM audit_log WHERE entity_type = 'employee' AND entity_id = :id")
			.param("id", id)
			.query(String.class)
			.list();
	}

}
