package com.npdinfosgroup.api.planner.importer;

import java.util.List;

/**
 * Something the import found in a row that someone should review: a rejected row or a value
 * saved with a correction.
 *
 * @param rowNumber Excel row
 * @param employeeCode COD EMPL of the row, if any
 * @param employeeName employee name, if any
 * @param column column with the issue
 * @param value original value of that column
 * @param messageKey key of the {@code messages.properties} message that explains the issue
 * @param arguments values that complete the message
 */
public record ImportIssue(int rowNumber, String employeeCode, String employeeName, PlannerColumn column,
		String value, String messageKey, List<String> arguments) {

	public ImportIssue {
		arguments = List.copyOf(arguments);
	}

}
