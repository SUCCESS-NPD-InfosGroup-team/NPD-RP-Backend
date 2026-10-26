package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.Employee;

/**
 * Re-reads an employee after saving it, which Spring Data does not offer.
 */
public interface EmployeeRefresh {

	/**
	 * Loads again what the database set on insert: the generated columns (probation end, vacation
	 * month, dates) and the related catalogs, which are read-only in the entity.
	 *
	 * @param employee a saved and flushed employee
	 */
	void refresh(Employee employee);

}
