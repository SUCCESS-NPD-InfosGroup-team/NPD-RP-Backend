package com.npdinfosgroup.api.planner.web;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

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
 * The options of the form selectors, one catalog per call: only active values, in each catalog's
 * order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class CatalogControllerTest {

	private static final String CATALOGS = "/api/v1/planner/catalogs/";

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	/** Two active job titles and an inactive one. */
	@BeforeEach
	void createFixture() {
		jdbc.sql("INSERT INTO job_title (name) VALUES ('CT Zeta analista'), ('CT Alfa analista')").update();
		jdbc.sql("INSERT INTO job_title (name, active) VALUES ('CT Retirado', false)").update();
	}

	/** Leaves the database as the other tests expect it. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM job_title WHERE name LIKE 'CT %'").update();
	}

	/** Catalogs that grow with the data go by name; inactive values are never offered. */
	@Test
	void listsActiveValuesByName() {
		get("job-titles").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[?(@.name =~ /CT .*/)].name").isEqualTo(List.of("CT Alfa analista", "CT Zeta analista"))
			.jsonPath("$.items[0].id").isNumber();
	}

	/** Fixed catalogs keep their seeded order, and "No especificado" is left out. */
	@Test
	void listsFixedCatalogsInSeededOrder() {
		get("genders").expectStatus().isOk().expectBody().jsonPath("$.items[*].name")
			.isEqualTo(List.of("Femenino", "Masculino"));
		get("equipment").expectStatus().isOk().expectBody().jsonPath("$.items[*].name")
			.isEqualTo(List.of("Infos", "Cliente"));
		get("work-modes").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[0].name").isEqualTo("Presencial - L/V")
			.jsonPath("$.items[?(@.name == 'No especificado')]").doesNotExist();
	}

	/** Every catalog of the form answers with its items. */
	@ParameterizedTest
	@ValueSource(strings = { "positions", "business-units", "talent-reviews", "client-roles", "termination-reasons" })
	void everyCatalogAnswers(String catalog) {
		get(catalog).expectStatus().isOk().expectBody().jsonPath("$.items").isArray();
	}

	/** Statuses go as code and label, in their usual order. */
	@Test
	void listsStatuses() {
		get("statuses").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].code").isEqualTo(List.of("ACTIVO", "LICENCIA", "BAJA"))
			.jsonPath("$.items[0].label").isEqualTo("Activo");
	}

	/** An unknown catalog is a 404; without a token, a 401. */
	@Test
	void unknownCatalogOrNoToken() {
		get("levels").expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");

		client.get().uri(CATALOGS + "genders").exchange().expectStatus().isUnauthorized();
	}

	private RestTestClient.ResponseSpec get(String catalog) {
		return client.get().uri(CATALOGS + catalog).header(AUTHORIZATION, "Bearer guest").exchange();
	}

}
