package com.npdinfosgroup.api.planner.persistence.query;

/**
 * The version of an employee record, read in bulk so a list can carry it without loading each record.
 *
 * @param id employee id
 * @param version its optimistic-locking version
 */
public record EmployeeVersion(Long id, Integer version) {
}
