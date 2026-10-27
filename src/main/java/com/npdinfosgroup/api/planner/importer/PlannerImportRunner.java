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
 * Runs the planner import when the application starts, only if
 * {@code --planner.import.file=<path to the Excel>} is given.
 * <p>
 * The import runs once, before the platform goes live, so it is a command and not an exposed
 * endpoint. With {@code --spring.main.web-application-type=none} the application stops when it
 * ends. Running it again updates the data without duplicating it.
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
	 * Creates the command.
	 *
	 * @param properties paths of the Excel and the report
	 * @param reader Excel reader
	 * @param importService service that saves the data
	 * @param reportWriter report writer
	 */
	PlannerImportRunner(PlannerImportProperties properties, PlannerWorkbookReader reader,
			PlannerImportService importService, ImportReportWriter reportWriter) {
		this.properties = properties;
		this.reader = reader;
		this.importService = importService;
		this.reportWriter = reportWriter;
	}

	/**
	 * Reads the Excel, imports its rows and writes the report. The log only gets counts and the
	 * report path, never personal data.
	 *
	 * @param args application arguments (unused; configuration comes from properties)
	 * @throws IOException if the Excel cannot be read or the report cannot be written
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
