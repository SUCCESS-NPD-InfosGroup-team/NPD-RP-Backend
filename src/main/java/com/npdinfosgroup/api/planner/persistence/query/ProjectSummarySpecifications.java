package com.npdinfosgroup.api.planner.persistence.query;

import java.time.LocalDate;
import java.util.Locale;

import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.entity.ProjectSummary;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters of the projects list, built with the JPA Criteria API so the values are always bound as
 * parameters.
 */
public final class ProjectSummarySpecifications {

	/** Escape character of the LIKE patterns built from user text. */
	static final char LIKE_ESCAPE = '\\';

	private ProjectSummarySpecifications() {
	}

	/**
	 * Projects of a client.
	 *
	 * @param clientId client id
	 * @return the filter
	 */
	public static Specification<ProjectSummary> ofClient(long clientId) {
		return (root, query, cb) -> cb.equal(root.get("clientId"), clientId);
	}

	/**
	 * Only active projects.
	 *
	 * @return the filter
	 */
	public static Specification<ProjectSummary> active() {
		return (root, query, cb) -> cb.isTrue(root.get("active"));
	}

	/**
	 * Projects whose contract with the client ends on or before a date. Projects without contract end
	 * are left out.
	 *
	 * @param date last day
	 * @return the filter
	 */
	public static Specification<ProjectSummary> contractEndingBy(LocalDate date) {
		return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("contractEndDate"), date);
	}

	/**
	 * Projects whose contract with the client ends between two dates, both included. Projects without
	 * contract end are left out.
	 *
	 * @param from first day
	 * @param to last day
	 * @return the filter
	 */
	public static Specification<ProjectSummary> contractEndingBetween(LocalDate from, LocalDate to) {
		return (root, query, cb) -> cb.between(root.get("contractEndDate"), from, to);
	}

	/**
	 * Projects without a client contract end («Sin fecha»).
	 *
	 * @return the filter
	 */
	public static Specification<ProjectSummary> withoutContractEnd() {
		return (root, query, cb) -> cb.isNull(root.get("contractEndDate"));
	}

	/**
	 * Projects whose name or whose client's name contains a text, ignoring case. The {@code %},
	 * {@code _} and {@code \} the user types are matched literally, not as wildcards.
	 *
	 * @param text text to look for, already stripped and not blank
	 * @return the filter
	 */
	public static Specification<ProjectSummary> nameContains(String text) {
		String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
		return (root, query, cb) -> cb.or(
				cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
				cb.like(cb.lower(root.get("clientName")), pattern, LIKE_ESCAPE));
	}

	/**
	 * Escapes the LIKE wildcards of a user text so they are matched literally.
	 *
	 * @param text user text
	 * @return the text with {@code \}, {@code %} and {@code _} escaped with a backslash
	 */
	static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/**
	 * Projects with at least one collaborator of an NPD lead, not counting collaborators with a status.
	 *
	 * @param leaderId employee id of the lead
	 * @param excludedStatus status code of the collaborators that do not count (Baja)
	 * @return the filter
	 */
	public static Specification<ProjectSummary> withCollaboratorOf(long leaderId, String excludedStatus) {
		return (root, query, cb) -> {
			Subquery<Long> collaborators = query.subquery(Long.class);
			Root<Employee> employee = collaborators.from(Employee.class);
			collaborators.select(employee.get("id"))
				.where(cb.equal(employee.get("projectId"), root.get("id")),
						cb.equal(employee.get("leaderId"), leaderId),
						cb.notEqual(employee.get("status").get("code"), excludedStatus));
			return cb.exists(collaborators);
		};
	}

}
