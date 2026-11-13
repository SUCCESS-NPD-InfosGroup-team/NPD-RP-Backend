package com.npdinfosgroup.api.planner.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.npdinfosgroup.api.planner.dto.request.UpcomingEventCriteria;
import com.npdinfosgroup.api.planner.dto.request.UpcomingEventType;
import com.npdinfosgroup.api.planner.dto.response.EventNoticeResponse;
import com.npdinfosgroup.api.planner.dto.response.EventResourceResponse;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.UpcomingEventResponse;
import com.npdinfosgroup.api.planner.dto.response.UpcomingEventsPageResponse;
import com.npdinfosgroup.api.planner.dto.response.UpcomingEventsSummary;
import com.npdinfosgroup.api.planner.persistence.entity.UpcomingEvent;
import com.npdinfosgroup.api.planner.persistence.query.UpcomingEventSpecifications;
import com.npdinfosgroup.api.planner.persistence.repository.UpcomingEventRepository;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.dto.PageInfo;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the upcoming events list (HU-06): assignment ends, client contract ends, probation ends and
 * vacation starts within a window of days, plus the assignment ends already passed that nobody
 * extended ("Pendiente de confirmacion"), with a count per type for the type cards.
 * <p>
 * Dates are calculated with the application clock (America/Bogota), never with the browser's. The
 * e-mail notices do not exist yet, so every event goes with notice {@code NONE}.
 */
@Service
public class UpcomingEventQueryService {

	/** Windows of the "Ventana" filter, in days ahead. */
	static final Set<Integer> WINDOWS = Set.of(30, 60, 90);

	/** By date, and by id so events of the same day keep their order between pages. */
	private static final Sort LIST_ORDER = Sort.by("date", "id");

	private final UpcomingEventRepository events;

	private final Clock clock;

	/**
	 * Creates the service.
	 *
	 * @param events the dated events of the view
	 * @param clock gives today for the window and the days remaining
	 */
	UpcomingEventQueryService(UpcomingEventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	/**
	 * A page of the list, ordered by date, with the counts per type. The counts use the window, client
	 * and lead filters but not the type one, so each card always shows its own total.
	 *
	 * @param criteria the filters
	 * @param pageable page number and size; its sort is ignored, the order is always by date
	 * @return the counts and the page
	 * @throws ApiException {@code INVALID_REQUEST} if the window is not 30, 60 or 90 days
	 */
	@Transactional(readOnly = true)
	public UpcomingEventsPageResponse list(UpcomingEventCriteria criteria, Pageable pageable) {
		if (!WINDOWS.contains(criteria.windowDays())) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, List.of(), Map.of());
		}
		// One "today" for every query and for daysRemaining, even if the call crosses midnight
		LocalDate today = LocalDate.now(clock);
		Specification<UpcomingEvent> filters = filters(criteria, today);

		Map<UpcomingEventType, Long> byType = new EnumMap<>(UpcomingEventType.class);
		long total = 0;
		for (UpcomingEventType type : UpcomingEventType.values()) {
			long count = events.count(filters.and(UpcomingEventSpecifications.ofType(type, today)));
			byType.put(type, count);
			total += count;
		}

		Specification<UpcomingEvent> listed = criteria.type() == null ? filters
				: filters.and(UpcomingEventSpecifications.ofType(criteria.type(), today));
		Page<UpcomingEvent> page = events.findAll(listed,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER));
		List<UpcomingEventResponse> items = page.getContent().stream().map(event -> toResponse(event, today)).toList();
		return new UpcomingEventsPageResponse(new UpcomingEventsSummary(total, byType), items,
				new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
	}

	/**
	 * The window, client and lead filters, combined with AND.
	 *
	 * @param criteria what the user chose
	 * @param today today in the application time zone
	 * @return the combined filter
	 */
	private static Specification<UpcomingEvent> filters(UpcomingEventCriteria criteria, LocalDate today) {
		List<Specification<UpcomingEvent>> filters = new ArrayList<>();
		filters.add(UpcomingEventSpecifications.inWindow(today, today.plusDays(criteria.windowDays())));
		if (criteria.clientId() != null) {
			filters.add(UpcomingEventSpecifications.ofClient(criteria.clientId()));
		}
		if (criteria.leaderId() != null) {
			filters.add(UpcomingEventSpecifications.ofLeader(criteria.leaderId(), ResourceRequestValidator.TERMINATED));
		}
		return Specification.allOf(filters);
	}

	/**
	 * The type shown in the list: an assignment end already passed is "Pendiente de confirmacion".
	 *
	 * @param event row of the view
	 * @param today today in the application time zone
	 * @return the type
	 */
	static UpcomingEventType typeOf(UpcomingEvent event, LocalDate today) {
		UpcomingEventType kind = UpcomingEventType.valueOf(event.getKind());
		return kind == UpcomingEventType.ASSIGNMENT_END && event.getDate().isBefore(today)
				? UpcomingEventType.PENDING_CONFIRMATION : kind;
	}

	private static UpcomingEventResponse toResponse(UpcomingEvent event, LocalDate today) {
		EventResourceResponse resource = event.getEmployeeId() == null ? null
				: new EventResourceResponse(event.getEmployeeId(), event.getEmployeeCode(), event.getFullName(),
						event.getClientRole());
		return new UpcomingEventResponse(event.getId(), typeOf(event, today), event.getDate(),
				ChronoUnit.DAYS.between(today, event.getDate()), resource,
				event.getResourceCount() == null ? 0 : event.getResourceCount(),
				idName(event.getClientId(), event.getClientName()), idName(event.getProjectId(), event.getProjectName()),
				idName(event.getLeaderId(), event.getLeaderName()), EventNoticeResponse.none());
	}

	private static IdName idName(Long id, String name) {
		return id == null ? null : new IdName(id, name);
	}

}
