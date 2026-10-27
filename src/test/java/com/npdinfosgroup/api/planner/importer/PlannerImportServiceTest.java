package com.npdinfosgroup.api.planner.importer;

import static com.npdinfosgroup.api.planner.importer.TestRows.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.npdinfosgroup.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports rows into a real PostgreSQL with the migrations applied. Each test runs in a
 * transaction that is rolled back at the end, so tests do not affect each other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Transactional
class PlannerImportServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

	@Autowired
	private PlannerImportService service;

	@Autowired
	private JdbcClient jdbc;

	/**
	 * Imports valid rows, creates clients, projects and catalogs, and assigns leaders by code even
	 * when the leader comes later.
	 */
	@Test
	void importsEmployeesWithCatalogsAndLeaders() {
		List<PlannerRow> rows = List.of(
				row(3, "100").with(PlannerColumn.WORK_MODALITY, "Pesencial - L/S")
					.with(PlannerColumn.LEVEL, "*")
					.with(PlannerColumn.TALENT_REVIEW, "FUTURO LIDER")
					.with(PlannerColumn.STATUS, "Licencia")
					.build(),
				row(4, "900").with(PlannerColumn.FULL_NAME, "Lucia  Maria Lider Gomez")
					.with(PlannerColumn.CLIENT, "NPD-ADMNISTRACION")
					.with(PlannerColumn.PROJECT, "NPD-ADMINISTRACION")
					.with(PlannerColumn.JOB_TITLE, "analista de pruebas")
					.with(PlannerColumn.LEADER_CODE, "1")
					.with(PlannerColumn.LEADER_NAME, "MARIA OTRA")
					.with(PlannerColumn.CLIENT_CONTACT, null)
					.hireDate(TODAY.minusDays(30))
					.build());

		PlannerImportReport report = service.importRows(rows, TODAY);

		assertThat(report.rowsRead()).isEqualTo(2);
		assertThat(report.imported()).isEqualTo(2);
		assertThat(report.rejected()).isEmpty();
		assertThat(report.flagged()).extracting(ImportIssue::rowNumber, ImportIssue::column, ImportIssue::messageKey)
			.containsExactly(tuple(4, PlannerColumn.LEADER_CODE, "planner.import.flag.leader-not-found"));

		Map<String, Object> employee = jdbc.sql("""
				SELECT e.full_name, e.email, e.level, w.code AS modality, t.code AS talent, s.code AS status,
				       l.code AS leader, c.name AS client, p.name AS project, e.project_end_date
				FROM employee e
				JOIN work_modality w ON w.id = e.work_modality_id
				JOIN resource_status s ON s.id = e.status_id
				LEFT JOIN talent_review t ON t.id = e.talent_review_id
				LEFT JOIN employee l ON l.id = e.leader_id
				LEFT JOIN project p ON p.id = e.project_id
				LEFT JOIN client c ON c.id = p.client_id
				WHERE e.code = '100'""").query().singleRow();
		assertThat(employee).containsEntry("full_name", "Persona 100")
			.containsEntry("email", "persona100@example.com")
			.containsEntry("level", null)
			.containsEntry("modality", "PRESENCIAL_L_S")
			.containsEntry("talent", "FUTURO_LIDER")
			.containsEntry("status", "LICENCIA")
			.containsEntry("leader", "900")
			.containsEntry("client", "ACME")
			.containsEntry("project", "ACME-WEB");
		assertThat(jdbc.sql("SELECT is_internal FROM client WHERE name = 'NPD-ADMINISTRACION'").query(Boolean.class)
			.single()).isTrue();
		assertThat(jdbc.sql("SELECT full_name FROM employee WHERE code = '900'").query(String.class).single())
			.isEqualTo("Lucia Maria Lider Gomez");
		assertThat(jdbc.sql("SELECT count(*) FROM job_title").query(Long.class).single()).isEqualTo(1);
		// On probation only while the 90 days since the hire date have not ended on the import date
		assertThat(jdbc.sql("SELECT code, on_probation FROM employee ORDER BY code").query().listOfRows())
			.extracting(row -> row.get("code") + "=" + row.get("on_probation"))
			.containsExactly("100=false", "900=true");
	}

	/**
	 * Empty required values become "No especificado" and whatever needs a review is flagged.
	 */
	@Test
	void flagsValuesToReview() {
		List<PlannerRow> rows = List.of(
				row(3, "900").with(PlannerColumn.FULL_NAME, "Lucia Lider").with(PlannerColumn.LEADER_CODE, "900").build(),
				row(4, "101").with(PlannerColumn.CLIENT, null)
					.with(PlannerColumn.PROJECT, " ")
					.with(PlannerColumn.WORK_MODALITY, "-")
					.with(PlannerColumn.LEADER_NAME, "ANA OTRA")
					.projectEndDate(LocalDate.of(2026, 9, 30), "2026-09-30")
					.with(PlannerColumn.PROJECT_END_MONTH, "AGOSTO 2026")
					.build(),
				row(5, "102").with(PlannerColumn.CLIENT, "SIN PROYECTO ")
					.with(PlannerColumn.PROJECT, "SIN PROYECTO")
					.with(PlannerColumn.TERMINATION_REASON, "Recurso sin proyecto")
					.with(PlannerColumn.CLIENT_CONTACT, "otro@acme.example")
					.with(PlannerColumn.LEADER_CODE, null)
					.build(),
				row(6, "103").with(PlannerColumn.EMAIL, "PERSONA900@example.com")
					.with(PlannerColumn.CLIENT_CONTACT, "sin-arroba")
					.with(PlannerColumn.LEVEL, "0")
					.with(PlannerColumn.BUSINESS_UNIT, "XYZ")
					.projectEndDate(null, "INDEFINIDO")
					.with(PlannerColumn.PROJECT_END_MONTH, "DICIEMBRE 2030")
					.build(),
				row(7, "103").build(),
				row(8, "104").with(PlannerColumn.EMAIL, "correo-malo").projectEndDate(null, "pronto").build(),
				row(9, "105").projectEndDate(null, "INDEFINIDO").with(PlannerColumn.PROJECT_END_MONTH, "INDEFINIDO")
					.build());

		PlannerImportReport report = service.importRows(rows, TODAY);

		assertThat(report.imported()).isEqualTo(6);
		assertThat(report.rejected()).extracting(ImportIssue::rowNumber, ImportIssue::messageKey)
			.containsExactly(tuple(7, "planner.import.rejected.duplicate-code"));
		assertThat(report.flagged()).extracting(ImportIssue::rowNumber, ImportIssue::column, ImportIssue::messageKey)
			.containsExactly(
					tuple(3, PlannerColumn.LEADER_CODE, "planner.import.flag.leader-self"),
					tuple(4, PlannerColumn.WORK_MODALITY, "planner.import.flag.not-specified"),
					tuple(4, PlannerColumn.CLIENT, "planner.import.flag.not-specified"),
					tuple(4, PlannerColumn.PROJECT, "planner.import.flag.not-specified"),
					tuple(4, PlannerColumn.PROJECT_END_MONTH, "planner.import.flag.end-month-mismatch"),
					tuple(4, PlannerColumn.PROJECT_END_DATE, "planner.import.flag.expired"),
					tuple(4, PlannerColumn.LEADER_NAME, "planner.import.flag.leader-name-mismatch"),
					tuple(5, PlannerColumn.TERMINATION_REASON, "planner.import.flag.termination-while-active"),
					tuple(5, PlannerColumn.CLIENT_CONTACT, "planner.import.flag.contact-other-client"),
					tuple(5, PlannerColumn.PROJECT_END_DATE, "planner.import.flag.no-project-end-date"),
					tuple(5, PlannerColumn.LEADER_CODE, "planner.import.flag.leader-missing"),
					tuple(6, PlannerColumn.EMAIL, "planner.import.flag.duplicate-email"),
					tuple(6, PlannerColumn.LEVEL, "planner.import.flag.unknown"),
					tuple(6, PlannerColumn.BUSINESS_UNIT, "planner.import.flag.unknown"),
					tuple(6, PlannerColumn.CLIENT_CONTACT, "planner.import.flag.invalid-email"),
					tuple(6, PlannerColumn.PROJECT_END_MONTH, "planner.import.flag.end-month-mismatch"),
					tuple(8, PlannerColumn.EMAIL, "planner.import.flag.invalid-email"),
					tuple(8, PlannerColumn.PROJECT_END_DATE, "planner.import.flag.unknown"),
					tuple(8, PlannerColumn.PROJECT_END_MONTH, "planner.import.flag.end-month-mismatch"));
	}

	/**
	 * Rows with missing or unknown required values, or with a repeated code, are rejected, and
	 * their flags are not reported.
	 */
	@Test
	void rejectsRowsThatCannotBeImported() {
		List<PlannerRow> rows = List.of(
				row(3, "100").build(),
				row(4, "100").build(),
				row(5, "101").with(PlannerColumn.GENDER, null).with(PlannerColumn.WORK_MODALITY, null).build(),
				row(6, "102").with(PlannerColumn.GENDER, "X")
					.with(PlannerColumn.STATUS, "Suspendido")
					.with(PlannerColumn.WORK_MODALITY, "Teletrabajo")
					.build(),
				row(7, "103").hireDate(null).build(),
				row(8, null).with(PlannerColumn.FULL_NAME, null).with(PlannerColumn.STATUS, null).build());

		PlannerImportReport report = service.importRows(rows, TODAY);

		assertThat(report.imported()).isEqualTo(1);
		assertThat(report.rejectedRows()).isEqualTo(5);
		assertThat(report.flagged()).extracting(ImportIssue::rowNumber).containsOnly(3);
		assertThat(report.rejected()).extracting(ImportIssue::rowNumber, ImportIssue::column, ImportIssue::messageKey)
			.containsExactly(
					tuple(4, PlannerColumn.EMPLOYEE_CODE, "planner.import.rejected.duplicate-code"),
					tuple(5, PlannerColumn.GENDER, "planner.import.rejected.missing"),
					tuple(6, PlannerColumn.GENDER, "planner.import.rejected.unknown"),
					tuple(6, PlannerColumn.STATUS, "planner.import.rejected.unknown"),
					tuple(6, PlannerColumn.WORK_MODALITY, "planner.import.rejected.unknown"),
					tuple(7, PlannerColumn.HIRE_DATE, "planner.import.rejected.missing"),
					tuple(8, PlannerColumn.EMPLOYEE_CODE, "planner.import.rejected.missing"),
					tuple(8, PlannerColumn.FULL_NAME, "planner.import.rejected.missing"),
					tuple(8, PlannerColumn.STATUS, "planner.import.rejected.missing"));
		assertThat(report.rejected().get(0).arguments()).containsExactly("3");
	}

	/**
	 * Running the import again updates employees without duplicating anything.
	 */
	@Test
	void reimportUpdatesInsteadOfDuplicating() {
		service.importRows(List.of(row(3, "900").build(), row(4, "100").build()), TODAY);

		service.importRows(List.of(row(3, "900").build(),
				row(4, "100").with(PlannerColumn.FULL_NAME, "Persona Corregida").with(PlannerColumn.LEADER_CODE, "777")
					.build()),
				TODAY);

		assertThat(jdbc.sql("SELECT count(*) FROM employee").query(Long.class).single()).isEqualTo(2);
		assertThat(jdbc.sql("SELECT count(*) FROM project WHERE name = 'ACME-WEB'").query(Long.class).single())
			.isEqualTo(1);
		Map<String, Object> employee = jdbc.sql("SELECT full_name, version, leader_id FROM employee WHERE code = '100'")
			.query()
			.singleRow();
		assertThat(employee).containsEntry("full_name", "Persona Corregida")
			.containsEntry("version", 1)
			.containsEntry("leader_id", null);
	}

}
