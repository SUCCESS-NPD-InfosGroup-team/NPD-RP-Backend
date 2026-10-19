package com.npdinfosgroup.api.planner.importer;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del import del planificador ({@code planner.import.*}). Se pasa como argumento al
 * arrancar la aplicación, solo cuando se quiere importar.
 *
 * @param file ruta del Excel del planificador (obligatoria)
 * @param report ruta del reporte a generar; si falta, se crea junto al Excel
 */
@ConfigurationProperties("planner.import")
record PlannerImportProperties(Path file, Path report) {

	static final String DEFAULT_REPORT_NAME = "reporte-import-planificador.xlsx";

	PlannerImportProperties {
		Objects.requireNonNull(file, "planner.import.file is required");
	}

	/**
	 * Ruta donde se escribe el reporte.
	 *
	 * @return {@code report} si se indicó; si no, {@value #DEFAULT_REPORT_NAME} en la carpeta del Excel
	 */
	Path reportFile() {
		return report != null ? report : file.toAbsolutePath().resolveSibling(DEFAULT_REPORT_NAME);
	}

}
