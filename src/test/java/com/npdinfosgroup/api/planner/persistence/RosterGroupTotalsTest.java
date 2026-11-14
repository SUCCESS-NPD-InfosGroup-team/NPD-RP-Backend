package com.npdinfosgroup.api.planner.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.sql.Types;

import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.planner.persistence.entity.RosterEntry;
import com.npdinfosgroup.api.planner.persistence.query.RosterEntrySpecifications;
import com.npdinfosgroup.api.planner.persistence.repository.RosterEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The totals of the roster groups in one query: one entry per project with matching rows, plus the
 * {@code null} key for the people without project, narrowed by the same filters as the search.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RosterGroupTotalsTest {

	@Autowired
	private RosterEntryRepository roster;

	@Autowired
	private JdbcClient jdbc;

	private long positionId;

	private long alphaId;

	private long betaId;

	/** Two projects (three and one people) and two people without project, one of them on level 2. */
	@BeforeEach
	void createRoster() {
		positionId = insert("INSERT INTO job_position (name) VALUES ('GT Analista') RETURNING id");
		long clientId = insert("INSERT INTO client (name) VALUES ('GT CLIENTE') RETURNING id");
		alphaId = insert("INSERT INTO project (client_id, name) VALUES (" + clientId + ", 'GT Alpha') RETURNING id");
		betaId = insert("INSERT INTO project (client_id, name) VALUES (" + clientId + ", 'GT Beta') RETURNING id");
		insertEmployee("GT-1", alphaId, 3);
		insertEmployee("GT-2", alphaId, 2);
		insertEmployee("GT-3", alphaId, 3);
		insertEmployee("GT-4", betaId, 3);
		insertEmployee("GT-5", null, 2);
		insertEmployee("GT-6", null, 3);
	}

	/** Deletes the rows of the test. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM employee WHERE code LIKE 'GT-%'").update();
		jdbc.sql("DELETE FROM project WHERE name LIKE 'GT %'").update();
		jdbc.sql("DELETE FROM client WHERE name LIKE 'GT %'").update();
		jdbc.sql("DELETE FROM job_position WHERE name LIKE 'GT %'").update();
	}

	/** Without other filters, every group of the test, including the people without project under the null key. */
	@Test
	void countsEveryGroup() {
		assertThat(roster.countByGroup(ofThisTest())).containsOnly(entry(alphaId, 3L),
				entry(betaId, 1L), entry(null, 2L));
	}

	/** The filters narrow the totals, and a group left without rows is absent. */
	@Test
	void countsOnlyTheFilteredRows() {
		assertThat(roster.countByGroup(ofThisTest().and(levelTwo()))).containsOnly(entry(alphaId, 1L), entry(null, 1L));
	}

	/** Only the rows of this test, whatever other tests left in the database. */
	private static Specification<RosterEntry> ofThisTest() {
		return RosterEntrySpecifications.containing("gt-");
	}

	private static Specification<RosterEntry> levelTwo() {
		return RosterEntrySpecifications.equalTo("level", (short) 2);
	}

	private long insert(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private void insertEmployee(String code, Long project, int level) {
		jdbc.sql("""
				INSERT INTO employee (code, full_name, gender_id, job_position_id, level, hire_date, status_id,
				                      project_id)
				VALUES (:code, :code, (SELECT id FROM gender WHERE code = 'F'), :position, :level, DATE '2025-01-06',
				        (SELECT id FROM resource_status WHERE code = 'ACTIVO'), :project)""")
			.param("code", code)
			.param("position", positionId)
			.param("level", level)
			.param("project", project, Types.BIGINT)
			.update();
	}

}
