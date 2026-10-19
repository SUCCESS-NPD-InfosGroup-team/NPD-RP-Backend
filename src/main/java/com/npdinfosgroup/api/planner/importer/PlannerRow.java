package com.npdinfosgroup.api.planner.importer;

import java.time.LocalDate;
import java.util.Map;

/**
 * A planner row as read from the Excel, before cleaning.
 *
 * @param rowNumber row number in the Excel (starting at 1), for the report
 * @param values original text of each column; empty columns may be missing
 * @param hireDate FECHA DE INICIO, if the cell is a date
 * @param projectEndDate FECHA FINAL PROYECTO, if the cell is a date ("INDEFINIDO" becomes
 *        {@code null})
 */
public record PlannerRow(int rowNumber, Map<PlannerColumn, String> values, LocalDate hireDate,
		LocalDate projectEndDate) {

	public PlannerRow {
		values = Map.copyOf(values);
	}

	/**
	 * Original text of a column.
	 *
	 * @param column column to read
	 * @return the text as it comes in the Excel, or {@code null} if the cell is empty
	 */
	public String raw(PlannerColumn column) {
		return values.get(column);
	}

	/**
	 * Clean text of a column, as {@link PlannerValues#clean(String)} returns it.
	 *
	 * @param column column to read
	 * @return the clean text, or {@code null} if the cell has no data
	 */
	public String get(PlannerColumn column) {
		return PlannerValues.clean(values.get(column));
	}

}
