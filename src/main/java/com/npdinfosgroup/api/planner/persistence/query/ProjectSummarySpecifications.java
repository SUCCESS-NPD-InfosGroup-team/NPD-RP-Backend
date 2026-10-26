package com.npdinfosgroup.api.planner.persistence.query;

import java.time.LocalDate;

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
