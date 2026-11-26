package com.npdinfosgroup.api.planner.persistence.query;

/**
 * The employee record of an NPD lead user, with how many collaborators still have them as lead.
 *
 * @param id employee id
 * @param employeeNumber N° de empleado
 * @param statusCode employment status code (ACTIVO, LICENCIA, BAJA)
 * @param collaborators employees whose lead is this one, Baja excluded
 */
public record NpdLeadEmployeeRow(Long id, String employeeNumber, String statusCode, Long collaborators) {
}
