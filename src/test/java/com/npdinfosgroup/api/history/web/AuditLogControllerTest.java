package com.npdinfosgroup.api.history.web;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.time.Instant;
import java.util.List;

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
 * "Auditoría" of a resource (HU-04), end to end: the answer, its filters, security and errors.
 * <p>
 * The endpoint only reads the audit log, so the tests write audit rows directly and delete them after.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class AuditLogControllerTest {

	private static final String AUDIT_LOG = "/api/v1/history/resources/{id}/audit-log";

	private static final String ADMIN = "operations-administrator";

	private static final long RESOURCE = 930_001;

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	/** A registration of two fields and a later edit of one, by another user. */
	@BeforeEach
	void writeLog() {
		insert("CREATE", "employeeNumber", null, "1045", "al.maria", Instant.parse("2026-08-03T13:15:00Z"));
		insert("CREATE", "name", null, "Ana Torres", "al.maria", Instant.parse("2026-08-03T13:15:00Z"));
		insert("UPDATE", "assignment.endDate", "2026-09-30", "2026-10-31", "al.jorge",
				Instant.parse("2026-10-02T15:14:00Z"));
	}

	/** Removes the rows the tests wrote. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log WHERE actor LIKE 'al.%'").update();
	}

	/**
	 * The Operations administrator gets the edit first and the registration as one entry with every value,
	 * plus the page and the options of the filters.
	 */
	@Test
	void returnsTheLogToTheOperationsAdministrator() {
		get(ADMIN, AUDIT_LOG).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].action").isEqualTo(List.of("UPDATE", "CREATE"))
			.jsonPath("$.items[0].actor").isEqualTo("al.jorge")
			.jsonPath("$.items[0].occurredAt").isEqualTo("2026-10-02T15:14:00Z")
			.jsonPath("$.items[0].fields[0].label").isEqualTo("Fecha final")
			.jsonPath("$.items[0].fields[0].oldValue").isEqualTo("2026-09-30")
			.jsonPath("$.items[0].fields[0].newValue").isEqualTo("2026-10-31")
			.jsonPath("$.items[1].fields[*].field").isEqualTo(List.of("employeeNumber", "name"))
			.jsonPath("$.page.totalElements").isEqualTo(2)
			.jsonPath("$.page.size").isEqualTo(25)
			.jsonPath("$.actors").isEqualTo(List.of("al.jorge", "al.maria"))
			.jsonPath("$.fields[0].field").isEqualTo("assignment.endDate");
	}

	/** The query parameters reach the filters: a user and a date range in Bogotá days. */
	@Test
	void appliesTheFilters() {
		get(ADMIN, AUDIT_LOG + "?actor=al.maria&from=2026-08-03&to=2026-08-03").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].action").isEqualTo(List.of("CREATE"))
			.jsonPath("$.actors.length()").isEqualTo(2);
	}

	/**
	 * Only the Operations administrator can read the log.
	 *
	 * @param role realm role used as the token
	 */
	@ParameterizedTest
	@ValueSource(strings = { "npd-leader", "customer-success", "guest" })
	void forbidsTheOtherRoles(String role) {
		get(role, AUDIT_LOG).expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
	}

	/** Without a valid token nothing is read. */
	@Test
	void requiresAValidToken() {
		client.get().uri(AUDIT_LOG, RESOURCE).exchange().expectStatus().isUnauthorized();
		get(TestSecurityConfiguration.INVALID_TOKEN, AUDIT_LOG).expectStatus().isUnauthorized();
	}

	/** A resource never changed, or an unknown id, answers an empty page. */
	@Test
	void answersAnEmptyPageWithoutChanges() {
		client.get()
			.uri(AUDIT_LOG, 999_999_999)
			.header(AUTHORIZATION, "Bearer " + ADMIN)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items").isEmpty()
			.jsonPath("$.page.totalElements").isEqualTo(0);
	}

	/** A malformed date is a bad request, and "Hasta" before "Desde" is an error on {@code to}. */
	@Test
	void rejectsBadDates() {
		get(ADMIN, AUDIT_LOG + "?from=2026-13-01").expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("INVALID_REQUEST");
		get(ADMIN, AUDIT_LOG + "?from=2026-10-02&to=2026-10-01").expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
			.jsonPath("$.errors[0].field").isEqualTo("to")
			.jsonPath("$.errors[0].code").isEqualTo("CONFLICTING_VALUES")
			.jsonPath("$.errors[0].message").isEqualTo("La fecha «Hasta» no puede ser anterior a «Desde».");
	}

	private RestTestClient.ResponseSpec get(String token, String uri) {
		return client.get().uri(uri, RESOURCE).header(AUTHORIZATION, "Bearer " + token).exchange();
	}

	private void insert(String action, String field, String oldValue, String newValue, String actor,
			Instant occurredAt) {
		jdbc.sql("""
				INSERT INTO audit_log (entity_type, entity_id, action, field, old_value, new_value, actor, occurred_at)
				VALUES ('employee', :id, :action, :field, :old, :new, :actor, :at)""")
			.param("id", RESOURCE)
			.param("action", action)
			.param("field", field)
			.param("old", oldValue)
			.param("new", newValue)
			.param("actor", actor)
			.param("at", java.sql.Timestamp.from(occurredAt))
			.update();
	}

}
