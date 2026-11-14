package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.npdinfosgroup.api.planner.dto.request.UpcomingEventCriteria;
import com.npdinfosgroup.api.planner.dto.request.UpcomingEventType;
import com.npdinfosgroup.api.planner.dto.response.EventNoticeResponse;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.UpcomingEventResponse;
import com.npdinfosgroup.api.planner.dto.response.UpcomingEventsPageResponse;
import com.npdinfosgroup.api.planner.dto.response.UpcomingEventsStripResponse;
import com.npdinfosgroup.api.planner.persistence.entity.UpcomingEvent;
import com.npdinfosgroup.api.planner.persistence.repository.UpcomingEventRepository;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor.SpecificationFluentQuery;

/**
 * The upcoming events list without a database: the counts per type, the mapping of each row, the
 * Bogota date as today and the accepted windows. The filters themselves are tested against the view
 * in {@code UpcomingEventSpecificationsTest}.
 */
class UpcomingEventQueryServiceTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	/** 23:30 in Bogota is already the next day in UTC: today must be the Bogota date. */
	private static final Clock CLOCK = Clock.fixed(
			LocalDate.of(2026, 11, 11).atTime(23, 30).atZone(BOGOTA).toInstant(), BOGOTA);

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

	private final UpcomingEventRepository events = mock();

	private final UpcomingEventQueryService service = new UpcomingEventQueryService(events, CLOCK);

	/** One count per type, in the order of the cards, and a total that adds them all. */
	@Test
	@SuppressWarnings("unchecked")
	void countsEveryTypeEvenWithATypeFilter() {
		when(events.count(any(Specification.class))).thenReturn(1L, 2L, 3L, 4L, 5L);
		when(events.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

		UpcomingEventsPageResponse page = service.list(
				new UpcomingEventCriteria(30, 4L, 3L, UpcomingEventType.CONTRACT_END), PageRequest.of(0, 25));

		assertThat(page.summary().total()).isEqualTo(15);
		assertThat(page.summary().byType()).containsExactly(
				Map.entry(UpcomingEventType.PENDING_CONFIRMATION, 1L),
				Map.entry(UpcomingEventType.ASSIGNMENT_END, 2L),
				Map.entry(UpcomingEventType.CONTRACT_END, 3L),
				Map.entry(UpcomingEventType.PROBATION_END, 4L),
				Map.entry(UpcomingEventType.VACATION_START, 5L));
		assertThat(page.items()).isEmpty();
	}

	/**
	 * A passed assignment end goes as "Pendiente de confirmacion" with negative days; one due today is
	 * still an assignment end; a contract end has no person but counts its team.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void mapsEachRowWithTheBogotaDate() {
		UpcomingEvent pending = event("ASSIGNMENT_END-1", "ASSIGNMENT_END", TODAY.minusDays(3), 1L);
		UpcomingEvent dueToday = event("ASSIGNMENT_END-2", "ASSIGNMENT_END", TODAY, 2L);
		UpcomingEvent contractEnd = event("CONTRACT_END-9", "CONTRACT_END", TODAY.plusDays(20), null);
		when(contractEnd.getResourceCount()).thenReturn(4L);
		when(events.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(pending, dueToday, contractEnd), PageRequest.of(0, 25), 3));

		List<UpcomingEventResponse> items = service.list(new UpcomingEventCriteria(30, null, null, null),
				PageRequest.of(0, 25)).items();

		assertThat(items).extracting(UpcomingEventResponse::type).containsExactly(
				UpcomingEventType.PENDING_CONFIRMATION, UpcomingEventType.ASSIGNMENT_END, UpcomingEventType.CONTRACT_END);
		assertThat(items).extracting(UpcomingEventResponse::daysRemaining).containsExactly(-3L, 0L, 20L);
		assertThat(items.get(0).resource().employeeNumber()).isEqualTo("E-1");
		assertThat(items.get(0).client()).isEqualTo(new IdName(7, "TIGO"));
		assertThat(items.get(0).project()).isNull();
		assertThat(items.get(0).notice()).isEqualTo(EventNoticeResponse.none());
		assertThat(items.get(2).resource()).isNull();
		assertThat(items.get(2).resourceCount()).isEqualTo(4);
	}

	/** The list is always by date, whatever sort the client asks for; the page and size are kept. */
	@Test
	@SuppressWarnings("unchecked")
	void ignoresTheRequestedSort() {
		when(events.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

		service.list(new UpcomingEventCriteria(90, null, null, null), PageRequest.of(2, 10, Sort.by("leaderName")));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(events).findAll(any(Specification.class), pageable.capture());
		assertThat(pageable.getValue()).isEqualTo(PageRequest.of(2, 10, Sort.by("date", "id")));
	}

	/**
	 * The strip: the total of the 30-day window with one count, and the four nearest events by date,
	 * read without a page count and mapped like the list.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void summarisesTheNextThirtyDays() {
		UpcomingEvent probation = event("PROBATION_END-2", "PROBATION_END", TODAY.plusDays(11), 2L);
		SpecificationFluentQuery<UpcomingEvent> query = mock();
		when(query.sortBy(any(Sort.class))).thenReturn(query);
		when(query.limit(anyInt())).thenReturn(query);
		when(query.all()).thenReturn(List.of(probation));
		when(events.count(any(Specification.class))).thenReturn(8L);
		when(events.findBy(any(Specification.class), any(Function.class)))
			.thenAnswer(call -> call.<Function<SpecificationFluentQuery<UpcomingEvent>, ?>>getArgument(1).apply(query));

		UpcomingEventsStripResponse strip = service.summary();

		assertThat(strip.windowDays()).isEqualTo(30);
		assertThat(strip.total()).isEqualTo(8);
		assertThat(strip.nearest()).singleElement().satisfies(event -> {
			assertThat(event.type()).isEqualTo(UpcomingEventType.PROBATION_END);
			assertThat(event.daysRemaining()).isEqualTo(11);
			assertThat(event.resource().employeeNumber()).isEqualTo("E-2");
		});
		verify(events).count(any(Specification.class));
		verify(query).sortBy(Sort.by("date", "id"));
		verify(query).limit(4);
		verify(events, never()).findAll(any(Specification.class), any(Pageable.class));
	}

	/** Only the windows of the "Ventana" filter are accepted, before any query. */
	@Test
	void rejectsOtherWindows() {
		assertThatThrownBy(() -> service.list(new UpcomingEventCriteria(45, null, null, null), PageRequest.of(0, 25)))
			.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
		verifyNoInteractions(events);
	}

	private static UpcomingEvent event(String id, String kind, LocalDate date, Long employeeId) {
		UpcomingEvent event = mock();
		when(event.getId()).thenReturn(id);
		when(event.getKind()).thenReturn(kind);
		when(event.getDate()).thenReturn(date);
		when(event.getEmployeeId()).thenReturn(employeeId);
		when(event.getEmployeeCode()).thenReturn(employeeId == null ? null : "E-" + employeeId);
		when(event.getResourceCount()).thenReturn(1L);
		when(event.getClientId()).thenReturn(7L);
		when(event.getClientName()).thenReturn("TIGO");
		when(event.getProjectId()).thenReturn(null);
		when(event.getLeaderId()).thenReturn(null);
		return event;
	}

}
