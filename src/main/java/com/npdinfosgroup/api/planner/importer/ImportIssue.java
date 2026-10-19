package com.npdinfosgroup.api.planner.importer;

import java.util.List;

/**
 * Algo que el import encontró en una fila y que alguien debe revisar: una fila rechazada o un
 * valor que se guardó con una corrección.
 *
 * @param rowNumber fila del Excel
 * @param employeeCode COD EMPL de la fila, si lo tiene
 * @param employeeName nombre del empleado, si lo tiene
 * @param column columna donde está el problema
 * @param value valor original de esa columna
 * @param messageKey clave del mensaje en {@code messages.properties} que explica el problema
 * @param arguments datos que completan el mensaje
 */
public record ImportIssue(int rowNumber, String employeeCode, String employeeName, PlannerColumn column,
		String value, String messageKey, List<String> arguments) {

	public ImportIssue {
		arguments = List.copyOf(arguments);
	}

}
