package com.npdinfosgroup.api.planner.importer;

import java.util.List;

/**
 * Result of a planner import.
 *
 * @param rowsRead rows with data in the Excel
 * @param imported employees created or updated
 * @param rejected reasons why some rows were not imported
 * @param flagged values imported with a correction that should be reviewed
 */
public record PlannerImportReport(int rowsRead, int imported, List<ImportIssue> rejected, List<ImportIssue> flagged) {

	public PlannerImportReport {
		rejected = List.copyOf(rejected);
		flagged = List.copyOf(flagged);
	}

	/**
	 * Rows that were not imported.
	 *
	 * @return rows read minus rows imported
	 */
	public int rejectedRows() {
		return rowsRead - imported;
	}

}
