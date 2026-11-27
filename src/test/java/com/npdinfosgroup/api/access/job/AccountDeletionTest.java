package com.npdinfosgroup.api.access.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * CA-08 end to end, with a deletion delay of two seconds: an account deactivated through the API is
 * deleted from Keycloak and from {@code app_user} at its time, and the audit keeps its name and email.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.access.account-deletion-delay=PT2S")
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class AccountDeletionTest {

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	@MockitoBean
	private KeycloakAdminClient keycloak;

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM audit_log WHERE entity_type = 'app_user'").update();
		jdbc.sql("DELETE FROM app_user").update();
	}

	/** The deactivated account disappears at its deletion time, with the audit of who did what. */
	@Test
	void deletesTheAccountAtItsTime() throws InterruptedException {
		UUID keycloakId = UUID.randomUUID();
		long id = jdbc.sql("""
				INSERT INTO app_user (keycloak_id, full_name, email, role, state, created_at, created_by)
				VALUES (:keycloakId, 'Ana Torres', 'ana.torres@example.com', 'guest', 'ACTIVE', now(), 'test')
				RETURNING id""").param("keycloakId", keycloakId).query(Long.class).single();

		client.post().uri("/api/v1/access/users/" + id + "/deactivation")
			.header(AUTHORIZATION, "Bearer operations-administrator").contentType(APPLICATION_JSON)
			.body(Map.of("version", 0)).exchange()
			.expectStatus().isOk();

		Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
		while (exists(id) && Instant.now().isBefore(deadline)) {
			Thread.sleep(200);
		}
		assertThat(exists(id)).isFalse();
		verify(keycloak).deleteAccount(keycloakId);
		assertThat(jdbc.sql("""
				SELECT action || ' ' || field || ': ' || coalesce(old_value, '') || ' -> ' || coalesce(new_value, '')
				       || ' by ' || actor
				FROM audit_log WHERE entity_type = 'app_user' AND entity_id = :id ORDER BY id""")
			.param("id", id).query(String.class).list())
			.isEqualTo(List.of("UPDATE state: ACTIVE -> DISABLED by " + TestSecurityConfiguration.usernameOf("operations-administrator"),
					"DELETE fullName: Ana Torres ->  by system", "DELETE email: ana.torres@example.com ->  by system",
					"DELETE role: guest ->  by system"));
	}

	private boolean exists(long id) {
		return jdbc.sql("SELECT count(*) FROM app_user WHERE id = :id").param("id", id).query(Long.class).single() > 0;
	}

}
