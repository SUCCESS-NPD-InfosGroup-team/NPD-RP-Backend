package com.npdinfosgroup.api.planner.persistence;

import static com.npdinfosgroup.api.planner.UpcomingEventFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.planner.UpcomingEventFixture;
import com.npdinfosgroup.api.planner.persistence.entity.UpcomingEvent;
import com.npdinfosgroup.api.planner.persistence.repository.UpcomingEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The {@code upcoming_event_v} view (HU-06) read through its entity: one row per dated event, without
 * time window, with Baja and inactive projects left out.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UpcomingEventViewTest {

	@Autowired
	private UpcomingEventRepository events;

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
	 * The assignment ends (also the passed one), the probation end of who is still on probation and
	 * the contract end of the active project. Dario (Baja) and "UE Cerrado" (inactive) give no events.
	 */
	@Test
	void givesOneRowPerEventOfTheActivePeople() {
		assertThat(ofClient(fixture.banescoId))
			.filteredOn(event -> !event.getKind().equals("VACATION_START"))
			.extracting(UpcomingEvent::getKind, UpcomingEvent::getDate, UpcomingEvent::getFullName)
			.containsExactly(
					tuple("ASSIGNMENT_END", TODAY.minusDays(3), "UE Ana"),
					tuple("ASSIGNMENT_END", TODAY.plusDays(10), "UE Beto"),
					tuple("PROBATION_END", TODAY.plusDays(10), "UE Beto"),
					tuple("CONTRACT_END", TODAY.plusDays(20), null));
	}

	/** A contract end is for the whole team without Baja, with the lead they all share. */
	@Test
	void contractEndCountsTheTeamAndItsLead() {
		UpcomingEvent contractEnd = ofClient(fixture.banescoId).stream()
			.filter(event -> event.getKind().equals("CONTRACT_END"))
			.findFirst()
			.orElseThrow();
		assertThat(contractEnd.getEmployeeId()).isNull();
		assertThat(contractEnd.getResourceCount()).isEqualTo(2);
		assertThat(contractEnd.getProjectName()).isEqualTo("UE Portal");
		assertThat(contractEnd.getLeaderId()).isEqualTo(fixture.lorenaId);
		assertThat(contractEnd.getLeaderName()).isEqualTo("UE Lorena");
	}

	/** Employee events carry the person, their client role, project and lead. */
	@Test
	void employeeEventsCarryThePerson() {
		UpcomingEvent assignmentEnd = ofClient(fixture.tigoId).stream()
			.filter(event -> event.getKind().equals("ASSIGNMENT_END"))
			.findFirst()
			.orElseThrow();
		assertThat(assignmentEnd.getId()).startsWith("ASSIGNMENT_END-");
		assertThat(assignmentEnd.getEmployeeCode()).isEqualTo("UE-6");
		assertThat(assignmentEnd.getResourceCount()).isEqualTo(1);
		assertThat(assignmentEnd.getProjectName()).isEqualTo("UE Soporte");
		assertThat(assignmentEnd.getLeaderId()).isEqualTo(fixture.marioId);
	}

	/**
	 * The vacation starts the first day of the vacation month, this year and next year, once the first
	 * vacation (11 months after hire) has come.
	 */
	@Test
	void vacationStartsTheFirstDayOfItsMonth() {
		assertThat(ofClient(fixture.tigoId))
			.filteredOn(event -> event.getKind().equals("VACATION_START"))
			.extracting(UpcomingEvent::getDate)
			.contains(UpcomingEventFixture.nextMonthStart())
			.allMatch(date -> date.getDayOfMonth() == 1);
	}

	private List<UpcomingEvent> ofClient(long clientId) {
		return events.findAll((root, query, cb) -> cb.equal(root.get("clientId"), clientId),
				Sort.by("date", "id"));
	}

}
