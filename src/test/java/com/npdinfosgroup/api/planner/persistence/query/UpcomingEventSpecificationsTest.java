package com.npdinfosgroup.api.planner.persistence.query;

import static com.npdinfosgroup.api.planner.UpcomingEventFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.planner.UpcomingEventFixture;
import com.npdinfosgroup.api.planner.dto.request.UpcomingEventType;
import com.npdinfosgroup.api.planner.persistence.entity.UpcomingEvent;
import com.npdinfosgroup.api.planner.persistence.repository.UpcomingEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The filters of the upcoming events list (HU-06) against the real view: the window, the split of the
 * assignment ends in pending and upcoming, the client and the NPD lead.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UpcomingEventSpecificationsTest {

	private static final String BAJA = "BAJA";

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

	/** The window keeps the events up to its last day and every passed assignment end. */
	@Test
	void windowKeepsUpcomingAndPendingEvents() {
		Specification<UpcomingEvent> tigo = UpcomingEventSpecifications.ofClient(fixture.tigoId);
		// Next month's first day is at most 31 days away; Carla's assignment end is 50 days away
		assertThat(dates(tigo.and(window(45)))).containsExactly(UpcomingEventFixture.nextMonthStart());
		assertThat(dates(tigo.and(window(90))))
			.containsExactly(UpcomingEventFixture.nextMonthStart(), TODAY.plusDays(50), TODAY.plusDays(75));
		assertThat(dates(UpcomingEventSpecifications.ofClient(fixture.banescoId).and(window(30))))
			.containsExactly(TODAY.minusDays(3), TODAY.plusDays(10), TODAY.plusDays(10), TODAY.plusDays(20));
	}

	/**
	 * What is still coming leaves out the passed assignment end and keeps the last day of the window, but
	 * not the day after.
	 */
	@Test
	void upcomingLeavesOutPendingAndLaterEvents() {
		Specification<UpcomingEvent> banesco = UpcomingEventSpecifications.ofClient(fixture.banescoId);
		assertThat(dates(banesco.and(UpcomingEventSpecifications.upcoming(TODAY, TODAY.plusDays(20)))))
			.containsExactly(TODAY.plusDays(10), TODAY.plusDays(10), TODAY.plusDays(20));
		assertThat(dates(banesco.and(UpcomingEventSpecifications.upcoming(TODAY, TODAY.plusDays(19)))))
			.containsExactly(TODAY.plusDays(10), TODAY.plusDays(10));
	}

	/** A passed assignment end is "Pendiente de confirmacion"; the others are upcoming assignment ends. */
	@Test
	void splitsAssignmentEndsByToday() {
		Specification<UpcomingEvent> banesco = UpcomingEventSpecifications.ofClient(fixture.banescoId);
		assertThat(dates(banesco.and(type(UpcomingEventType.PENDING_CONFIRMATION))))
			.containsExactly(TODAY.minusDays(3));
		assertThat(dates(banesco.and(type(UpcomingEventType.ASSIGNMENT_END)))).containsExactly(TODAY.plusDays(10));
		assertThat(dates(banesco.and(type(UpcomingEventType.CONTRACT_END)))).containsExactly(TODAY.plusDays(20));
		assertThat(dates(banesco.and(type(UpcomingEventType.PROBATION_END)))).containsExactly(TODAY.plusDays(10));
	}

	/**
	 * The lead filter keeps their collaborators' events and the contract ends of the projects where
	 * they have a collaborator, but not other projects' contracts.
	 */
	@Test
	void filtersByLead() {
		Specification<UpcomingEvent> mario = UpcomingEventSpecifications.ofLeader(fixture.marioId, BAJA)
			.and(window(90));
		assertThat(events.findAll(mario, Sort.by("date", "id")))
			.extracting(UpcomingEvent::getKind)
			.containsExactly("VACATION_START", "ASSIGNMENT_END", "CONTRACT_END");
		assertThat(events.findAll(UpcomingEventSpecifications.ofLeader(fixture.lorenaId, BAJA)
			.and(type(UpcomingEventType.CONTRACT_END))))
			.extracting(UpcomingEvent::getProjectName)
			.containsExactly("UE Portal");
	}

	private static Specification<UpcomingEvent> window(int days) {
		return UpcomingEventSpecifications.inWindow(TODAY, TODAY.plusDays(days));
	}

	private static Specification<UpcomingEvent> type(UpcomingEventType type) {
		return UpcomingEventSpecifications.ofType(type, TODAY);
	}

	private List<LocalDate> dates(Specification<UpcomingEvent> filter) {
		return events.findAll(filter, Sort.by("date", "id")).stream().map(UpcomingEvent::getDate).toList();
	}

}
