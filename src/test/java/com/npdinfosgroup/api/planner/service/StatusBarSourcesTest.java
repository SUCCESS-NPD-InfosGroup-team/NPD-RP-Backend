package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.access.service.UserQueryService;
import com.npdinfosgroup.api.history.dto.response.LatestChange;
import com.npdinfosgroup.api.history.service.AuditQueryService;
import com.npdinfosgroup.api.planner.UpcomingEventFixture;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The pieces the status bar reads from each module (HU-11, design Decisions 2 and 3), against a real
 * PostgreSQL: the latest change of the whole audit log, the name of its author and the counts of active
 * rows.
 * <p>
 * Other tests share the database, so the counts are checked as the difference the fixture makes, and
 * the audit rows of this test are dated in the future so they are the latest ones.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class StatusBarSourcesTest {

	private static final long AUDITED_ID = 930_001;

	@Autowired
	private AuditQueryService audit;

	@Autowired
	private UserQueryService users;

	@Autowired
	private EmployeeRepository employees;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private ClientRepository clients;

	@Autowired
	private JdbcClient jdbc;

	private UpcomingEventFixture fixture;

	/** Deletes the rows this test wrote. */
	@AfterEach
	void cleanUp() {
		if (fixture != null) {
			fixture.cleanUp();
		}
		jdbc.sql("DELETE FROM audit_log WHERE entity_id = :id").param("id", AUDITED_ID).update();
		jdbc.sql("DELETE FROM app_user WHERE email = 'sb.maria@example.com'").update();
	}

	/** The latest row of any record wins; two rows of the same moment are ordered by their id. */
	@Test
	void findsTheLatestChangeOfTheWholeLog() {
		Instant latest = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		audit("sb.older", latest.minus(1, ChronoUnit.HOURS));
		audit("sb.first", latest);
		audit("sb.second", latest);

		assertThat(audit.findLatestChange()).contains(new LatestChange("sb.second", latest));
	}

	/**
	 * A username that is the email of a user becomes the user's full name, whatever its case; any other
	 * username stays as it is.
	 */
	@Test
	void namesTheAuthorWhenItIsAUser() {
		jdbc.sql("""
				INSERT INTO app_user (keycloak_id, full_name, email, role, state, created_at, created_by)
				VALUES (gen_random_uuid(), 'María Pérez', 'sb.maria@example.com', 'guest', 'ACTIVE', now(), 'test')""")
			.update();

		assertThat(users.displayNameOf("SB.Maria@Example.com")).isEqualTo("María Pérez");
		assertThat(users.displayNameOf("sb.console-only")).isEqualTo("sb.console-only");
	}

	/**
	 * The fixture adds 2 active clients, 3 projects of which 1 is inactive, and 6 employees of which 1 is
	 * Baja: 2 clients, 2 projects and 5 resources more are counted.
	 */
	@Test
	void countsOnlyTheActiveRows() {
		long resources = employees.countByStatus_Code(ProjectQueryService.ACTIVE_STATUS);
		long activeProjects = projects.countByActiveTrue();
		long activeClients = clients.countByActiveTrue();

		fixture = new UpcomingEventFixture(jdbc);
		fixture.create();

		assertThat(employees.countByStatus_Code(ProjectQueryService.ACTIVE_STATUS) - resources).isEqualTo(5);
		assertThat(projects.countByActiveTrue() - activeProjects).isEqualTo(2);
		assertThat(clients.countByActiveTrue() - activeClients).isEqualTo(2);
	}

	private void audit(String actor, Instant occurredAt) {
		jdbc.sql("""
				INSERT INTO audit_log (entity_type, entity_id, action, field, old_value, new_value, actor, occurred_at)
				VALUES ('project', :id, 'UPDATE', 'name', 'Antes', 'Después', :actor, :occurredAt)""")
			.param("id", AUDITED_ID)
			.param("actor", actor)
			.param("occurredAt", java.sql.Timestamp.from(occurredAt))
			.update();
	}

}
