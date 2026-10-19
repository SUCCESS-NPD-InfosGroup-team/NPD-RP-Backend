package com.npdinfosgroup.api.planner.importer;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Corre el import del planificador al arrancar la aplicación, solo si se indica
 * {@code --planner.import.file=<ruta del Excel>}.
 * <p>
 * El import se hace una sola vez, antes de que la plataforma entre en uso; por eso es un comando y
 * no un endpoint que quede expuesto. Con {@code --spring.main.web-application-type=none} la
 * aplicación termina sola al acabar. Volver a correrlo actualiza los datos sin duplicarlos.
 */
@Component
@ConditionalOnProperty(prefix = "planner.import", name = "file")
@EnableConfigurationProperties(PlannerImportProperties.class)
class PlannerImportRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PlannerImportRunner.class);

	private final PlannerImportProperties properties;

	private final PlannerWorkbookReader reader;

	private final PlannerImportService importService;

	private final ImportReportWriter reportWriter;

	/**
	 * Crea el comando.
	 *
	 * @param properties rutas del Excel y del reporte
	 * @param reader lector del Excel
	 * @param importService servicio que guarda los datos
	 * @param reportWriter escritor del reporte
	 */
	PlannerImportRunner(PlannerImportProperties properties, PlannerWorkbookReader reader,
			PlannerImportService importService, ImportReportWriter reportWriter) {
		this.properties = properties;
		this.reader = reader;
		this.importService = importService;
		this.reportWriter = reportWriter;
	}

	/**
	 * Lee el Excel, importa sus filas y escribe el reporte. En el log solo quedan cantidades y la
	 * ruta del reporte, nunca datos de las personas.
	 *
	 * @param args argumentos de la aplicación (no se usan; la configuración llega por propiedades)
	 * @throws IOException si no se puede leer el Excel o escribir el reporte
	 */
	@Override
	public void run(ApplicationArguments args) throws IOException {
		List<PlannerRow> rows = reader.read(properties.file());
		PlannerImportReport report = importService.importRows(rows, LocalDate.now());
		reportWriter.write(report, properties.reportFile());
		log.info("Planner import finished: {} rows read, {} imported, {} rejected, {} flagged. Report: {}",
				report.rowsRead(), report.imported(), report.rejectedRows(), report.flagged().size(),
				properties.reportFile());
	}

}
