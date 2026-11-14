package com.npdinfosgroup.api.history.web;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.time.Instant;
import java.util.List;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
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
 * "Último cambio" of the resource side panel (SCRUM-85), end to end: only the last operation, every
 * field of it with its label, registrations, resources never changed and security.
 * <p>
 * The endpoint only reads the audit log, so the tests write audit rows directly and delete them after.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class AuditControllerTest {

	private static final String LAST_CHANGE = "/api/v1/history/resources/{id}/last-change";

	private static final long RESOURCE = 900_001;

	private static final Instant REGISTERED = Instant.parse("2026-09-01T14:00:00Z");

	private static final Instant EDITED = Instant.parse("2026-10-02T15:14:00Z");

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	/** Removes the rows the tests wrote. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log WHERE actor LIKE 'rt.%'").update();
	}

	/**
	 * Only the latest operation is returned, with every field it changed in the order they were
	 * written, each with its Spanish label; a field without a label keeps its technical name. Rows of
	 * another table with the same id are ignored.
	 */
	@Test
	void returnsEveryFieldOfTheLastOperationOnly() {
		insert("employee", RESOURCE, "CREATE", "name", null, "Ana Torres", "rt.admin", REGISTERED);
		insert("employee", RESOURCE, "UPDATE", "assignment.endDate", "2026-09-30", "2026-10-31", "rt.maria", EDITED);
		insert("employee", RESOURCE, "UPDATE", "assignment.leader", "Ricardo Him", "Lorena Batista", "rt.maria", EDITED);
		insert("employee", RESOURCE, "UPDATE", "future.field", "a", null, "rt.maria", EDITED);
		insert("project", RESOURCE, "UPDATE", "name", "Old", "New", "rt.other", EDITED.plusSeconds(60));

		get("guest", RESOURCE).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.action").isEqualTo("UPDATE")
			.jsonPath("$.actor").isEqualTo("rt.maria")
			.jsonPath("$.occurredAt").isEqualTo("2026-10-02T15:14:00Z")
			.jsonPath("$.fields[*].field").isEqualTo(List.of("assignment.endDate", "assignment.leader", "future.field"))
			.jsonPath("$.fields[*].label").isEqualTo(List.of("Fecha final", "Líder NPD", "future.field"))
			.jsonPath("$.fields[0].oldValue").isEqualTo("2026-09-30")
			.jsonPath("$.fields[0].newValue").isEqualTo("2026-10-31")
			.jsonPath("$.fields[2].newValue").doesNotExist();
	}

	/** A resource whose last operation is its registration shows it, without previous values. */
	@Test
	void returnsTheRegistration() {
		insert("employee", RESOURCE, "CREATE", "employeeNumber", null, "1045", "rt.admin", REGISTERED);
		insert("employee", RESOURCE, "CREATE", "status", null, "Activo", "rt.admin", REGISTERED);

		get("npd-leader", RESOURCE).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.action").isEqualTo("CREATE")
			.jsonPath("$.actor").isEqualTo("rt.admin")
			.jsonPath("$.fields[*].label").isEqualTo(List.of("N° de empleado", "Estado"))
			.jsonPath("$.fields[0].oldValue").doesNotExist()
			.jsonPath("$.fields[0].newValue").isEqualTo("1045");
	}

	/**
	 * A resource never changed through the API (imported) has no last change: 204 for every role, with
	 * no body.
	 *
	 * @param role realm role used as the token
	 */
	@ParameterizedTest
	@ValueSource(strings = { "operations-administrator", "npd-leader", "customer-success", "guest" })
	void answersNoContentWithoutChanges(String role) {
		get(role, RESOURCE + 1).expectStatus().isNoContent().expectBody().isEmpty();
	}

	/** Without a valid token nothing is read; a malformed id is a bad request. */
	@Test
	void requiresValidTokenAndNumericId() {
		client.get().uri(LAST_CHANGE, RESOURCE).exchange().expectStatus().isUnauthorized();
		get(TestSecurityConfiguration.INVALID_TOKEN, RESOURCE).expectStatus().isUnauthorized();
		client.get()
			.uri("/api/v1/history/resources/abc/last-change")
			.header(AUTHORIZATION, "Bearer guest")
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("INVALID_REQUEST");
	}

	private RestTestClient.ResponseSpec get(String token, long id) {
		return client.get().uri(LAST_CHANGE, id).header(AUTHORIZATION, "Bearer " + token).exchange();
	}

	private void insert(String entityType, long entityId, String action, String field, String oldValue,
			String newValue, String actor, Instant occurredAt) {
		jdbc.sql("""
				INSERT INTO audit_log (entity_type, entity_id, action, field, old_value, new_value, actor, occurred_at)
				VALUES (:type, :id, :action, :field, :old, :new, :actor, :at)""")
			.param("type", entityType)
			.param("id", entityId)
			.param("action", action)
			.param("field", field)
			.param("old", oldValue)
			.param("new", newValue)
			.param("actor", actor)
			.param("at", java.sql.Timestamp.from(occurredAt))
			.update();
	}

}
