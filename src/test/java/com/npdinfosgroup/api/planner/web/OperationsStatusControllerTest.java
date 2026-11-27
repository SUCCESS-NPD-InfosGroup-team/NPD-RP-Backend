package com.npdinfosgroup.api.planner.web;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The status bar through the API (HU-11 CA-01), end to end: the JSON the frontend reads and who can
 * read it. How each piece is computed is tested in {@code StatusBarSourcesTest},
 * {@code OperationsStatusServiceTest} and {@code NoticeScheduleServiceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class OperationsStatusControllerTest {

	private static final String STATUS = "/api/v1/planner/operations-status";

	private static final long AUDITED_ID = 930_002;

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	/** Deletes the audit row and the user of this test. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log WHERE entity_id = :id").param("id", AUDITED_ID).update();
		jdbc.sql("DELETE FROM app_user WHERE email = 'os.maria@example.com'").update();
	}

	/**
	 * The fields the frontend reads, with the author by name. No send is scheduled in the tests
	 * ({@code notice-cron} is «-»), so the next send is {@code null}.
	 */
	@Test
	void answersTheStatusOfTheData() {
		Instant latest = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		jdbc.sql("""
				INSERT INTO app_user (keycloak_id, full_name, email, role, state, created_at, created_by)
				VALUES (gen_random_uuid(), 'María Pérez', 'os.maria@example.com', 'guest', 'ACTIVE', now(), 'test')""")
			.update();
		jdbc.sql("""
				INSERT INTO audit_log (entity_type, entity_id, action, field, old_value, new_value, actor, occurred_at)
				VALUES ('project', :id, 'UPDATE', 'name', 'Antes', 'Después', 'os.maria@example.com', :occurredAt)""")
			.param("id", AUDITED_ID)
			.param("occurredAt", java.sql.Timestamp.from(latest))
			.update();

		get("guest").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.lastUpdatedAt").isEqualTo(latest.toString())
			.jsonPath("$.lastUpdatedBy").isEqualTo("María Pérez")
			.jsonPath("$.nextNotificationAt").isEqualTo(null)
			.jsonPath("$.activeResources").isNumber()
			.jsonPath("$.activeProjects").isNumber()
			.jsonPath("$.activeClients").isNumber();
	}

	/** Any role can read it; without a token or with an invalid one it is refused. */
	@Test
	void anyRoleCanReadIt() {
		get("operations-administrator").expectStatus().isOk();
		get("npd-leader").expectStatus().isOk();
		get("customer-success").expectStatus().isOk();
		client.get().uri(STATUS).exchange().expectStatus().isUnauthorized();
		get(TestSecurityConfiguration.INVALID_TOKEN).expectStatus().isUnauthorized();
	}

	private RestTestClient.ResponseSpec get(String token) {
		return client.get().uri(STATUS).header(AUTHORIZATION, "Bearer " + token).exchange();
	}

}
