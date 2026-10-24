package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Employees (the resources of the planner) for the create and edit operations.
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

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

}
