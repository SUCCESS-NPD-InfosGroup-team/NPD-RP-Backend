package com.npdinfosgroup.api.access.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.sql.DataSource;

import com.npdinfosgroup.api.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Migration V14 (SCRUM-231): the users still deactivated by the first version of the story are deleted
 * with their audit rows, and the others stay. It runs in a schema of its own, migrated to V13 first.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DropDeactivationMigrationTest {

	private static final String SCHEMA = "v14_check";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcClient jdbc;

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void dropSchema() {
		jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
	}

	/** A deactivated user is deleted and audited as a deletion by "system"; an active one is kept. */
	@Test
	void deletesTheDeactivatedUsers() {
		flyway("13").migrate();
		jdbc.sql("""
				INSERT INTO v14_check.app_user (keycloak_id, full_name, email, role, state, created_at, created_by,
				                                disabled_at)
				VALUES (gen_random_uuid(), 'Ana Torres', 'ana@example.com', 'guest', 'DISABLED', now(), 'test', now()),
				       (gen_random_uuid(), 'Luis Gómez', 'luis@example.com', 'guest', 'ACTIVE', now(), 'test', NULL)""")
			.update();

		flyway("14").migrate();

		assertThat(jdbc.sql("SELECT email FROM v14_check.app_user").query(String.class).list())
			.containsExactly("luis@example.com");
		assertThat(jdbc.sql("""
				SELECT action || ' ' || field || ': ' || old_value || ' by ' || actor FROM v14_check.audit_log
				WHERE entity_type = 'app_user' ORDER BY id""").query(String.class).list())
			.isEqualTo(List.of("DELETE fullName: Ana Torres by system", "DELETE email: ana@example.com by system",
					"DELETE role: guest by system"));
		assertThat(jdbc.sql("""
				SELECT count(*) FROM information_schema.columns
				WHERE table_schema = 'v14_check' AND table_name = 'app_user' AND column_name = 'disabled_at'""")
			.query(Integer.class).single()).isZero();
	}

	private Flyway flyway(String target) {
		return Flyway.configure()
			.dataSource(dataSource)
			.schemas(SCHEMA)
			.locations("classpath:db/migration")
			.target(target)
			.load();
	}

}
