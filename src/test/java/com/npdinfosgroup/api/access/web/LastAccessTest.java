package com.npdinfosgroup.api.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.sql.Timestamp;
import java.util.UUID;

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
 * Any API call with a user's own token records their access (design §5), end to end: the token's
 * subject is the user's Keycloak id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class LastAccessTest {

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM app_user").update();
	}

	/**
	 * The first call makes a pending user active and records the moment, and a second call right after
	 * does not write it again. A disabled user keeps that state.
	 */
	@Test
	void recordsTheAccessAndActivatesPendingUsers() {
		UUID ana = insertUser("ana@example.com", "PENDING");
		UUID luis = insertUser("luis@example.com", "DISABLED");

		callAs(ana);
		Timestamp first = column("last_access_at", ana, Timestamp.class);
		callAs(ana);
		callAs(luis);

		assertThat(column("state", ana, String.class)).isEqualTo("ACTIVE");
		assertThat(first).isNotNull().isEqualTo(column("last_access_at", ana, Timestamp.class));
		assertThat(column("state", luis, String.class)).isEqualTo("DISABLED");
		assertThat(column("last_access_at", luis, Timestamp.class)).isNotNull();
	}

	private void callAs(UUID user) {
		client.get().uri("/api/v1/planner/projects").header(AUTHORIZATION, "Bearer guest~" + user).exchange()
			.expectStatus()
			.isOk();
	}

	private UUID insertUser(String email, String state) {
		UUID keycloakId = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO app_user (keycloak_id, full_name, email, role, state, created_at, created_by)
				VALUES (:id, 'Test user', :email, 'guest', :state, now(), 'test')""")
			.param("id", keycloakId)
			.param("email", email)
			.param("state", state)
			.update();
		return keycloakId;
	}

	private <T> T column(String name, UUID user, Class<T> type) {
		return jdbc.sql("SELECT " + name + " FROM app_user WHERE keycloak_id = :id").param("id", user).query(type).single();
	}

}
