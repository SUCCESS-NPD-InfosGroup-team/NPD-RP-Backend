package com.npdinfosgroup.api.planner.web;

import static com.npdinfosgroup.api.planner.UpcomingEventFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.util.List;
import java.util.Map;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.planner.UpcomingEventFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The upcoming events list through the API (HU-06), end to end: the JSON the frontend reads, the
 * pagination, the rejected parameters and who can read it. The filters and the counting rules are
 * tested in {@code UpcomingEventSpecificationsTest} and {@code UpcomingEventQueryServiceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class UpcomingEventControllerTest {

	private static final String EVENTS = "/api/v1/planner/upcoming-events";

	@Autowired
	private RestTestClient client;

	@Autowired
	private JdbcClient jdbc;

	private UpcomingEventFixture fixture;

	/** Inserts the clients, projects and employees of {@link UpcomingEventFixture}. */
	@BeforeEach
	void createFixture() {
		fixture = new UpcomingEventFixture(jdbc);
		fixture.create();
	}

	/** Deletes the rows of the fixture. */
	@AfterEach
	void cleanUp() {
		fixture.cleanUp();
	}

	/**
	 * The events of a client by date, with the shape the frontend adapter expects: the passed
	 * assignment end first, and the contract end without person but with its team and lead.
	 */
	@Test
	void listsTheEventsOfAClient() {
		list("guest", "windowDays=30&clientId=" + fixture.banescoId).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].type")
			.isEqualTo(List.of("PENDING_CONFIRMATION", "ASSIGNMENT_END", "PROBATION_END", "CONTRACT_END"))
			.jsonPath("$.items[*].daysRemaining").isEqualTo(List.of(-3, 10, 10, 20))
			.jsonPath("$.items[0].resource.name").isEqualTo("UE Ana")
			.jsonPath("$.items[0].resource.employeeNumber").isEqualTo("UE-3")
			.jsonPath("$.items[0].client.name").isEqualTo("UE BANESCO")
			.jsonPath("$.items[0].project.name").isEqualTo("UE Portal")
			.jsonPath("$.items[0].leader.id").isEqualTo(fixture.lorenaId)
			.jsonPath("$.items[0].notice.status").isEqualTo("NONE")
			.jsonPath("$.items[3].resource").isEqualTo(null)
			.jsonPath("$.items[3].resourceCount").isEqualTo(2)
			.jsonPath("$.items[3].date").isEqualTo(TODAY.plusDays(20).toString())
			.jsonPath("$.summary.total").isEqualTo(4)
			.jsonPath("$.summary.byType.PENDING_CONFIRMATION").isEqualTo(1)
			.jsonPath("$.summary.byType.VACATION_START").isEqualTo(0)
			.jsonPath("$.page.size").isEqualTo(25);
	}

	/** Pages are counted from 0, keep the date order, and the type filter leaves the counts alone. */
	@Test
	void paginatesAndFiltersByType() {
		String banesco = "clientId=" + fixture.banescoId;
		list("guest", banesco + "&page=1&size=2").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].type").isEqualTo(List.of("PROBATION_END", "CONTRACT_END"))
			.jsonPath("$.page.number").isEqualTo(1)
			.jsonPath("$.page.totalPages").isEqualTo(2)
			.jsonPath("$.page.totalElements").isEqualTo(4);
		list("guest", banesco + "&type=PENDING_CONFIRMATION").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.items[*].resource.name").isEqualTo(List.of("UE Ana"))
			.jsonPath("$.summary.total").isEqualTo(4);
	}

	/**
	 * The strip of Recursos: the same total as the list opened without filters, and as nearest events the
	 * first four rows of that list once Ana's passed assignment end is left out. Carla's vacation (first
	 * day of next month) moves with the day the test runs, so the order is taken from the list itself.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void summarisesTheStrip() {
		Map<String, Object> list = list("guest", "windowDays=30").expectStatus()
			.isOk()
			.expectBody(Map.class)
			.returnResult()
			.getResponseBody();
		Object listTotal = ((Map<String, Object>) list.get("summary")).get("total");
		List<Object> upcomingIds = ((List<Map<String, Object>>) list.get("items")).stream()
			.filter(item -> !"PENDING_CONFIRMATION".equals(item.get("type")))
			.limit(4)
			.map(item -> item.get("id"))
			.toList();

		summary("npd-leader").expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.windowDays").isEqualTo(30)
			.jsonPath("$.total").isEqualTo(listTotal)
			.jsonPath("$.nearest[*].id").isEqualTo(upcomingIds)
			.jsonPath("$.nearest[?(@.type == 'PENDING_CONFIRMATION')]").isEmpty()
			.jsonPath("$.nearest[?(@.type == 'ASSIGNMENT_END')].resource.name").isEqualTo(List.of("UE Beto"))
			.jsonPath("$.nearest[?(@.type == 'ASSIGNMENT_END')].daysRemaining").isEqualTo(List.of(10))
			.jsonPath("$.nearest[?(@.type == 'ASSIGNMENT_END')].client.name").isEqualTo(List.of("UE BANESCO"));
		assertThat(upcomingIds).hasSizeBetween(3, 4);
	}

	/** Any role can read the strip; without a token it is refused. */
	@Test
	void anyRoleCanReadTheStrip() {
		summary("guest").expectStatus().isOk();
		summary("customer-success").expectStatus().isOk();
		client.get().uri(EVENTS + "/summary").exchange().expectStatus().isUnauthorized();
	}

	/** Only windows of 30, 60 or 90 days and known types are accepted. */
	@Test
	void rejectsUnknownWindowsAndTypes() {
		list("guest", "windowDays=45").expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("INVALID_REQUEST");
		list("guest", "type=SOMEDAY").expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.code").isEqualTo("INVALID_REQUEST");
	}

	/** Any role can read the list; without a token it is refused. */
	@Test
	void anyRoleCanReadIt() {
		list("customer-success", "windowDays=90").expectStatus().isOk();
		list("npd-leader", "windowDays=60").expectStatus().isOk();
		client.get().uri(EVENTS).exchange().expectStatus().isUnauthorized();
	}

	private RestTestClient.ResponseSpec list(String token, String query) {
		return client.get().uri(EVENTS + "?" + query).header(AUTHORIZATION, "Bearer " + token).exchange();
	}

	private RestTestClient.ResponseSpec summary(String token) {
		return client.get().uri(EVENTS + "/summary").header(AUTHORIZATION, "Bearer " + token).exchange();
	}

}
