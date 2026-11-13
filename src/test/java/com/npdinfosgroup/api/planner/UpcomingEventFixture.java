package com.npdinfosgroup.api.planner;

import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Rows for the upcoming events tests (HU-06), with every date relative to today so the tests do not
 * depend on the day they run.
 * <p>
 * Banesco: "UE Portal" (contract ends in 20 days) with Ana (assignment ended 3 days ago), Beto
 * (assignment and probation end in 10 days) and Dario (Baja), all led by Lorena; and "UE Cerrado",
 * inactive. Tigo: "UE Soporte" (contract ends in 75 days) with Carla (assignment ends in 50 days,
 * vacation next month, her first vacation already reached), led by Mario. Lorena, Mario and Ana have their vacation six months away.
 */
public class UpcomingEventFixture {

	/** Today in the application time zone. */
	public static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

	/** Hire date whose vacation month is six months away, so it never falls in a 90-day window. */
	private static final LocalDate FAR_VACATION_HIRE = TODAY.minusYears(2).minusMonths(5);

	private final JdbcClient jdbc;

	public long banescoId;

	public long tigoId;

	public long lorenaId;

	public long marioId;

	/**
	 * Creates the fixture; {@link #create()} inserts the rows.
	 *
	 * @param jdbc connection of the test
	 */
	public UpcomingEventFixture(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** @return the first day of next month, which is Carla's vacation start */
	public static LocalDate nextMonthStart() {
		return TODAY.plusMonths(1).withDayOfMonth(1);
	}

	/** Inserts the clients, projects and employees described in the class comment. */
	public void create() {
		banescoId = insert("INSERT INTO client (name) VALUES ('UE BANESCO') RETURNING id");
		tigoId = insert("INSERT INTO client (name) VALUES ('UE TIGO') RETURNING id");
		long portalId = insertProject(banescoId, "UE Portal", TODAY.plusDays(20), true);
		insertProject(banescoId, "UE Cerrado", TODAY.plusDays(5), false);
		long soporteId = insertProject(tigoId, "UE Soporte", TODAY.plusDays(75), true);
		jdbc.sql("INSERT INTO job_position (name) VALUES ('UE Posicion')").update();
		lorenaId = insertEmployee("UE-1", "UE Lorena", "ACTIVO", null, null, null, FAR_VACATION_HIRE, false);
		marioId = insertEmployee("UE-2", "UE Mario", "ACTIVO", null, null, null, FAR_VACATION_HIRE, false);
		insertEmployee("UE-3", "UE Ana", "ACTIVO", portalId, lorenaId, TODAY.minusDays(3), FAR_VACATION_HIRE, false);
		insertEmployee("UE-4", "UE Beto", "ACTIVO", portalId, lorenaId, TODAY.plusDays(10), TODAY.minusDays(80), true);
		insertEmployee("UE-5", "UE Dario", "BAJA", portalId, lorenaId, TODAY.plusDays(5), TODAY.minusDays(80), true);
		insertEmployee("UE-6", "UE Carla", "ACTIVO", soporteId, marioId, TODAY.plusDays(50),
				nextMonthStart().minusMonths(11).minusYears(1), false);
	}

	/** Deletes every row of the fixture, so the database is as the other tests expect it. */
	public void cleanUp() {
		jdbc.sql("UPDATE employee SET leader_id = NULL WHERE code LIKE 'UE-%'").update();
		jdbc.sql("DELETE FROM employee WHERE code LIKE 'UE-%'").update();
		jdbc.sql("DELETE FROM project WHERE client_id IN (SELECT id FROM client WHERE name LIKE 'UE %')").update();
		jdbc.sql("DELETE FROM client WHERE name LIKE 'UE %'").update();
		jdbc.sql("DELETE FROM job_position WHERE name LIKE 'UE %'").update();
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
			LocalDate projectEnd, LocalDate hireDate, boolean onProbation) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, hire_date, status_id, project_id,
				                      leader_id, project_end_date, on_probation)
				VALUES (:code, :name,
				        (SELECT id FROM gender WHERE code = 'F'),
				        (SELECT id FROM job_position WHERE name = 'UE Posicion'),
				        :hireDate,
				        (SELECT id FROM resource_status WHERE code = :status),
				        :project, :leader, :projectEnd, :onProbation)
				RETURNING id""")
			.param("code", code)
			.param("name", name)
			.param("status", status)
			.param("project", project, Types.BIGINT)
			.param("leader", leader, Types.BIGINT)
			.param("projectEnd", projectEnd, Types.DATE)
			.param("hireDate", hireDate, Types.DATE)
			.param("onProbation", onProbation)
			.query(Long.class)
			.single();
	}

}
