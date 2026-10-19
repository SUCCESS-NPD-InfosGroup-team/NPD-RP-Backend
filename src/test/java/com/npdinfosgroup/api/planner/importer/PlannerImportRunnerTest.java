package com.npdinfosgroup.api.planner.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * The import command and its settings.
 */
class PlannerImportRunnerTest {

	/**
	 * Reads the Excel, imports its rows and writes the report next to the Excel by default.
	 */
	@Test
	void importsAndWritesReportNextToTheFile() throws IOException {
		Path file = Path.of("datos", "planner.xlsx");
		PlannerWorkbookReader reader = mock(PlannerWorkbookReader.class);
		PlannerImportService service = mock(PlannerImportService.class);
		ImportReportWriter writer = mock(ImportReportWriter.class);
		List<PlannerRow> rows = List.of(TestRows.row(3, "100").build());
		PlannerImportReport report = new PlannerImportReport(1, 1, List.of(), List.of());
		when(reader.read(file)).thenReturn(rows);
		when(service.importRows(any(), any())).thenReturn(report);
		PlannerImportRunner runner = new PlannerImportRunner(new PlannerImportProperties(file, null), reader, service,
				writer);

		runner.run(new DefaultApplicationArguments());

		verify(writer).write(report, file.toAbsolutePath().resolveSibling(PlannerImportProperties.DEFAULT_REPORT_NAME));
	}

	/**
	 * Paths come as {@code planner.import.*} properties.
	 */
	@Test
	void bindsProperties() {
		Binder binder = new Binder(new MapConfigurationPropertySource(
				Map.of("planner.import.file", "C:/datos/planner.xlsx", "planner.import.report", "reporte.xlsx")));

		PlannerImportProperties properties = binder.bind("planner.import", PlannerImportProperties.class).get();

		assertThat(properties.file()).isEqualTo(Path.of("C:/datos/planner.xlsx"));
		assertThat(properties.reportFile()).isEqualTo(Path.of("reporte.xlsx"));
	}

	/**
	 * Without the Excel path, the application does not start.
	 */
	@Test
	void requiresFile() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("planner.import.report", "r.xlsx")));

		assertThatThrownBy(() -> binder.bind("planner.import", PlannerImportProperties.class))
			.isInstanceOf(BindException.class);
	}

}
