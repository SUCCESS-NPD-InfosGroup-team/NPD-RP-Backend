package com.npdinfosgroup.api.planner.persistence.query;

import java.time.LocalDate;

import com.npdinfosgroup.api.planner.dto.request.UpcomingEventType;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.entity.UpcomingEvent;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters of the upcoming events list, built with the JPA Criteria API so the values are always bound
 * as parameters.
 * <p>
 * The view stores every assignment end as {@code ASSIGNMENT_END}; the ones whose date is before today
 * are the "Pendiente de confirmacion" of the list. Today always comes from the application clock.
 */
public final class UpcomingEventSpecifications {

	/** Kind of the view rows that are assignment ends, past or not. */
	static final String ASSIGNMENT_END = UpcomingEventType.ASSIGNMENT_END.name();

	/** Kind of the view rows that affect a whole project. */
	static final String CONTRACT_END = UpcomingEventType.CONTRACT_END.name();

	private UpcomingEventSpecifications() {
	}

	/**
	 * Events between today and the last day of the window, both included, plus every assignment end
	 * already passed, which is listed whatever the window (RQ-10).
	 *
	 * @param today today in the application time zone
	 * @param lastDay last day of the window
	 * @return the filter
	 */
	public static Specification<UpcomingEvent> inWindow(LocalDate today, LocalDate lastDay) {
		return (root, query, cb) -> cb.or(cb.between(root.get("date"), today, lastDay),
				cb.and(cb.equal(root.get("kind"), ASSIGNMENT_END), cb.lessThan(root.get("date"), today)));
	}

	/**
	 * Events between today and the last day of the window, both included, without the assignment ends
	 * already passed: what is still coming, for the strip of Recursos.
	 *
	 * @param today today in the application time zone
	 * @param lastDay last day of the window
	 * @return the filter
	 */
	public static Specification<UpcomingEvent> upcoming(LocalDate today, LocalDate lastDay) {
		return (root, query, cb) -> cb.between(root.get("date"), today, lastDay);
	}

	/**
	 * Events of one type of the list.
	 *
	 * @param type type shown in the list
	 * @param today today in the application time zone, which splits the assignment ends in pending and
	 *        upcoming
	 * @return the filter
	 */
	public static Specification<UpcomingEvent> ofType(UpcomingEventType type, LocalDate today) {
		return switch (type) {
			case PENDING_CONFIRMATION -> (root, query, cb) -> cb.and(cb.equal(root.get("kind"), ASSIGNMENT_END),
					cb.lessThan(root.get("date"), today));
			case ASSIGNMENT_END -> (root, query, cb) -> cb.and(cb.equal(root.get("kind"), ASSIGNMENT_END),
					cb.greaterThanOrEqualTo(root.get("date"), today));
			default -> (root, query, cb) -> cb.equal(root.get("kind"), type.name());
		};
	}

	/**
	 * Events of the projects of a client.
	 *
	 * @param clientId client id
	 * @return the filter
	 */
	public static Specification<UpcomingEvent> ofClient(long clientId) {
		return (root, query, cb) -> cb.equal(root.get("clientId"), clientId);
	}

	/**
	 * Events of the collaborators of an NPD lead. A client contract end matches when the project has at
	 * least one collaborator of the lead, as in the projects list.
	 *
	 * @param leaderId employee id of the lead
	 * @param excludedStatus status code of the collaborators that do not count (Baja)
	 * @return the filter
	 */
	public static Specification<UpcomingEvent> ofLeader(long leaderId, String excludedStatus) {
		return (root, query, cb) -> {
			Subquery<Long> collaborators = query.subquery(Long.class);
			Root<Employee> employee = collaborators.from(Employee.class);
			collaborators.select(employee.get("id"))
				.where(cb.equal(employee.get("projectId"), root.get("projectId")),
						cb.equal(employee.get("leaderId"), leaderId),
						cb.notEqual(employee.get("status").get("code"), excludedStatus));
			return cb.or(cb.and(cb.notEqual(root.get("kind"), CONTRACT_END), cb.equal(root.get("leaderId"), leaderId)),
					cb.and(cb.equal(root.get("kind"), CONTRACT_END), cb.exists(collaborators)));
		};
	}

}
