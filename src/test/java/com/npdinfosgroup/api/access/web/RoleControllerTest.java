package com.npdinfosgroup.api.access.web;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.List;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The roles for the cards of "Crear usuario" (CA-10), with their Spanish texts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class RoleControllerTest {

	private static final String ROLES = "/api/v1/access/roles";

	@Autowired
	private RestTestClient client;

	/**
	 * The roles that can be created come in order, each with its code, name and description. NPD lead
	 * is left out: it has its own registration (SCRUM-241 CA-10).
	 */
	@Test
	void listsTheAssignableRoles() {
		client.get().uri(ROLES).header(AUTHORIZATION, "Bearer operations-administrator").exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].code")
			.isEqualTo(List.of("operations-administrator", "customer-success", "guest"))
			.jsonPath("$.items[1].name").isEqualTo("Customer Success")
			.jsonPath("$.items[2].name").isEqualTo("Invitado")
			.jsonPath("$.items[0].description").isEqualTo("Gestiona recursos, proyectos y usuarios de la plataforma.");
	}

	/** Only Operations administrators see them. */
	@Test
	void onlyOperationsAdministratorsSeeTheRoles() {
		client.get().uri(ROLES).header(AUTHORIZATION, "Bearer npd-leader").exchange().expectStatus().isForbidden();
	}

}
