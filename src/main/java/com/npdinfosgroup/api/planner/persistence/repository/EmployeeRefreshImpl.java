package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import jakarta.persistence.EntityManager;

/**
 * Implementation of {@link EmployeeRefresh}, plugged into {@link EmployeeRepository} by Spring Data.
 */
class EmployeeRefreshImpl implements EmployeeRefresh {

	private final EntityManager entityManager;

	/**
	 * Creates the implementation.
	 *
	 * @param entityManager JPA entity manager
	 */
	EmployeeRefreshImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public void refresh(Employee employee) {
		entityManager.refresh(employee);
	}

}
