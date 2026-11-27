package com.npdinfosgroup.api.planner.persistence.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.query.EmployeeVersion;
import com.npdinfosgroup.api.planner.persistence.query.NpdLeadEmployeeRow;
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
	 * Employee with an e-mail, ignoring case like its unique index, to offer linking it to a user.
	 *
	 * @param email e-mail
	 * @return the employee, if any
	 */
	Optional<Employee> findFirstByEmailIgnoreCase(String email);

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
	 * Employees marked as NPD leads, with or without collaborators, by name, except those with the
	 * given status.
	 *
	 * @param excludedStatus status code left out (Baja)
	 * @return the leads
	 */
	@Query("""
			SELECT l FROM Employee l
			WHERE l.isNpdLead = true AND l.status.code <> :excludedStatus
			ORDER BY l.fullName""")
	List<Employee> findNpdLeadsExcludingStatus(@Param("excludedStatus") String excludedStatus);

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

	/**
	 * The members of a project with an end date, except those with a status, to move them with the
	 * project's contract end.
	 *
	 * @param projectId project id
	 * @param projectEndDate their end date in the project
	 * @param excludedStatus status code left out (Baja)
	 * @return the members
	 */
	List<Employee> findByProjectIdAndProjectEndDateAndStatus_CodeNot(Long projectId, LocalDate projectEndDate,
			String excludedStatus);

	/**
	 * How many people are on a project, except those with a status: its team size.
	 *
	 * @param projectId project id
	 * @param excludedStatus status code left out (Baja)
	 * @return the team size
	 */
	long countByProjectIdAndStatus_CodeNot(Long projectId, String excludedStatus);

	/**
	 * The versions of some employees, in one query, for lists that send them back on an action.
	 *
	 * @param ids employee ids; must not be empty
	 * @return one row per employee found
	 */
	@Query("""
			SELECT new com.npdinfosgroup.api.planner.persistence.query.EmployeeVersion(e.id, e.version)
			FROM Employee e WHERE e.id IN :ids""")
	List<EmployeeVersion> findVersions(@Param("ids") Collection<Long> ids);

	/**
	 * How many employees with a status have no project, for the «Colaboradores sin proyecto» KPI of
	 * the Projects module.
	 *
	 * @param statusCode status code of the employees counted (Activo)
	 * @return the number of employees
	 */
	@Query("SELECT count(e) FROM Employee e WHERE e.projectId IS NULL AND e.status.code = :statusCode")
	long countWithoutProject(@Param("statusCode") String statusCode);

	/**
	 * How many employees have a status, for the «recursos activos» count of the status bar (HU-11).
	 *
	 * @param statusCode status code of the employees counted (Activo)
	 * @return the number of employees
	 */
	long countByStatus_Code(String statusCode);

	/**
	 * The employee records of some NPD lead users, each with how many collaborators still have them as
	 * lead, in one query for a whole page of users.
	 *
	 * @param ids employee ids
	 * @param excludedStatus status code of the collaborators that do not count (Baja)
	 * @return one row per employee found
	 */
	@Query("""
			SELECT new com.npdinfosgroup.api.planner.persistence.query.NpdLeadEmployeeRow(
			       l.id, l.code, s.code,
			       (SELECT count(c) FROM Employee c JOIN c.status cs
			        WHERE c.leaderId = l.id AND cs.code <> :excludedStatus))
			FROM Employee l JOIN l.status s
			WHERE l.id IN :ids""")
	List<NpdLeadEmployeeRow> findNpdLeadEmployees(@Param("ids") Collection<Long> ids,
			@Param("excludedStatus") String excludedStatus);

}
