package com.npdinfosgroup.api.planner.importer;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Planner import settings ({@code planner.import.*}), passed as arguments when the application
 * starts, only to run the import.
 *
 * @param file path of the planner Excel (required)
 * @param report path of the report to write; if missing, it is written next to the Excel
 */
@ConfigurationProperties("planner.import")
record PlannerImportProperties(Path file, Path report) {

	static final String DEFAULT_REPORT_NAME = "reporte-import-planificador.xlsx";

	PlannerImportProperties {
		Objects.requireNonNull(file, "planner.import.file is required");
	}

	/**
	 * Path where the report is written.
	 *
	 * @return {@code report} if given; otherwise {@value #DEFAULT_REPORT_NAME} in the Excel's folder
	 */
	Path reportFile() {
		return report != null ? report : file.toAbsolutePath().resolveSibling(DEFAULT_REPORT_NAME);
	}

}
