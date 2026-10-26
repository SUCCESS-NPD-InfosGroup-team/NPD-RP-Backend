package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.Employee;
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

}
