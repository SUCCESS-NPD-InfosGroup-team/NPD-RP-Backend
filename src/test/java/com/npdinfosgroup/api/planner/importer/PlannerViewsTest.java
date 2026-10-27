package com.npdinfosgroup.api.planner.importer;

import static com.npdinfosgroup.api.planner.importer.TestRows.row;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
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
 * The views of migration V4 over imported rows: {@code resource_v} exposes ids and project dates,
 * and {@code project_summary_v} sums up each project's team. Each test is rolled back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Transactional
class PlannerViewsTest {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private PlannerImportService importService;

	@Autowired
	private JdbcClient jdbc;

	/**
	 * {@code resource_v} keeps its planner columns and adds the project and leader ids, the project
	 * dates and whether the project is active.
	 */
	@Test
	void resourceViewExposesIdsAndProjectDates() {
		importService.importRows(List.of(row(3, "900").with(PlannerColumn.LEADER_CODE, null).build(),
				row(4, "100").with(PlannerColumn.CLIENT, null).with(PlannerColumn.PROJECT, null).build()), TODAY);
		jdbc.sql("UPDATE project SET contract_end_date = DATE '2031-06-30' WHERE name = 'ACME-WEB'").update();

		Map<String, Object> leader = jdbc.sql("""
				SELECT r.client, r.project, r.project_id, r.client_id, r.project_active, r.project_start_date,
				       r.contract_end_date, r.leader_id, p.id AS expected_project_id, p.client_id AS expected_client_id
				FROM resource_v r
				JOIN project p ON p.name = 'ACME-WEB'
				WHERE r.employee_code = '900'""").query().singleRow();
		assertThat(leader).containsEntry("client", "ACME")
			.containsEntry("project", "ACME-WEB")
			.containsEntry("project_id", leader.get("expected_project_id"))
			.containsEntry("client_id", leader.get("expected_client_id"))
			.containsEntry("project_active", true)
			.containsEntry("contract_end_date", Date.valueOf("2031-06-30"))
			.containsEntry("leader_id", null);
		assertThat(leader.get("project_start_date")).isNotNull();

		Map<String, Object> notSpecified = jdbc.sql("""
				SELECT r.project, r.project_active, r.leader_id, l.id AS expected_leader_id
				FROM resource_v r
				JOIN employee l ON l.code = '900'
				WHERE r.employee_code = '100'""").query().singleRow();
		assertThat(notSpecified).containsEntry("project", "No especificado")
			.containsEntry("project_active", false)
			.containsEntry("leader_id", notSpecified.get("expected_leader_id"));
	}

	/**
	 * {@code project_summary_v} counts the team without Baja, the members to confirm and the
	 * indefinite ones, gives the next assignment end and lists projects without team.
	 */
	@Test
	void projectSummaryCountsTheTeam() {
		importService.importRows(List.of(
				row(3, "100").projectEndDate(TODAY.minusDays(5), TODAY.minusDays(5).toString())
					.with(PlannerColumn.PROJECT_END_MONTH, null)
					.build(),
				row(4, "101").projectEndDate(TODAY.plusDays(20), TODAY.plusDays(20).toString())
					.with(PlannerColumn.PROJECT_END_MONTH, null)
					.build(),
				row(5, "102").projectEndDate(TODAY.plusDays(90), TODAY.plusDays(90).toString())
					.with(PlannerColumn.PROJECT_END_MONTH, null)
					.build(),
				row(6, "103").projectEndDate(null, "INDEFINIDO").with(PlannerColumn.PROJECT_END_MONTH, null).build(),
				row(7, "104").with(PlannerColumn.STATUS, "Baja").build()), TODAY);
		jdbc.sql("""
				INSERT INTO project (client_id, name, contract_end_date)
				SELECT client_id, 'ACME-MOVIL', DATE '2031-01-31' FROM project WHERE name = 'ACME-WEB'""").update();

		Map<String, Object> web = summary("ACME-WEB");
		assertThat(web).containsEntry("client", "ACME")
			.containsEntry("is_internal", false)
			.containsEntry("active", true)
			.containsEntry("team_size", 4L)
			.containsEntry("pending_confirmation", 1L)
			.containsEntry("indefinite_count", 1L)
			.containsEntry("next_assignment_end", Date.valueOf(TODAY.plusDays(20)));
		assertThat(web.get("start_date")).isNotNull();

		Map<String, Object> mobile = summary("ACME-MOVIL");
		assertThat(mobile).containsEntry("team_size", 0L)
			.containsEntry("pending_confirmation", 0L)
			.containsEntry("next_assignment_end", null)
			.containsEntry("contract_end_date", Date.valueOf("2031-01-31"));
	}

	private Map<String, Object> summary(String project) {
		return jdbc.sql("SELECT * FROM project_summary_v WHERE project = :project")
			.param("project", project)
			.query()
			.singleRow();
	}

}
