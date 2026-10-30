package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.query.ProjectLead;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Employees (the resources of the planner) for the create, read and edit operations.
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long>, EmployeeRefresh {

	/**
	 * Employee with an employee number, ignoring case like the planner does.
	 *
	 * @param code employee number
	 * @return the employee, if the number is taken
	 */
	Optional<Employee> findFirstByCodeIgnoreCase(String code);

	/**
	 * Whether an e-mail already belongs to an employee, ignoring case like its unique index.
	 *
	 * @param email e-mail
	 * @return {@code true} if it is taken
	 */
	boolean existsByEmailIgnoreCase(String email);

	/**
	 * Employee with an employee number other than the given one, for the edit of that employee.
	 *
	 * @param code employee number
	 * @param id id of the employee being edited
	 * @return another employee with the number, if any
	 */
	Optional<Employee> findFirstByCodeIgnoreCaseAndIdNot(String code, Long id);

	/**
	 * Whether an e-mail belongs to an employee other than the given one, for the edit of that employee.
	 *
	 * @param email e-mail
	 * @param id id of the employee being edited
	 * @return {@code true} if another employee has it
	 */
	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

	/**
	 * An employee with every related catalog, project, client and lead, in one query, to build their
	 * full record.
	 *
	 * @param id employee id
	 * @return the employee, or empty if it does not exist
	 */
	@EntityGraph(attributePaths = { "gender", "jobTitle", "jobPosition", "businessUnit", "workModality", "status",
			"terminationReason", "talentReview", "project", "project.client", "leader", "clientRole",
			"equipmentProvider" })
	Optional<Employee> findDetailedById(Long id);

	/**
	 * Employees who lead at least one collaborator, by name, except those with the given status.
	 *
	 * @param excludedStatus status code left out (Baja)
	 * @return the leads
	 */
	@Query("""
			SELECT l FROM Employee l
			WHERE l.status.code <> :excludedStatus
			  AND EXISTS (SELECT 1 FROM Employee e WHERE e.leaderId = l.id)
			ORDER BY l.fullName""")
	List<Employee> findLeadsExcludingStatus(@Param("excludedStatus") String excludedStatus);

	/**
	 * The NPD leads of the collaborators of some projects, by name, not counting collaborators with a
	 * status.
	 *
	 * @param projectIds project ids
	 * @param excludedStatus status code of the collaborators that do not count (Baja)
	 * @return one row per project and lead
	 */
	@Query("""
			SELECT DISTINCT new com.npdinfosgroup.api.planner.persistence.query.ProjectLead(
			       e.projectId, l.id, l.fullName)
			FROM Employee e JOIN e.leader l
			WHERE e.projectId IN :projectIds AND e.status.code <> :excludedStatus
			ORDER BY l.fullName""")
	List<ProjectLead> findProjectLeads(@Param("projectIds") Collection<Long> projectIds,
			@Param("excludedStatus") String excludedStatus);

}
