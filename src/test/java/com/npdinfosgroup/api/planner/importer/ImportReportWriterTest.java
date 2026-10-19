package com.npdinfosgroup.api.planner.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * El reporte del import se escribe con los textos de {@code messages.properties}.
 */
class ImportReportWriterTest {

	@TempDir
	Path dir;

	/**
	 * Escribe el resumen, las filas rechazadas y las observaciones, creando la carpeta si no existe.
	 */
	@Test
	void writesSummaryRejectedAndFlaggedSheets() throws IOException {
		ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
		messages.setBasename("messages");
		messages.setDefaultEncoding("UTF-8");
		ImportReportWriter writer = new ImportReportWriter(messages);
		ImportIssue rejected = new ImportIssue(5, null, "Persona sin código", PlannerColumn.EMPLOYEE_CODE, null,
				"planner.import.rejected.missing", List.of());
		ImportIssue flagged = new ImportIssue(7, "200", "Persona 200", PlannerColumn.LEADER_NAME, "ANA RUIZ",
				"planner.import.flag.leader-name-mismatch", List.of("Lucia Lider"));
		Path target = dir.resolve("salida/reporte.xlsx");

		writer.write(new PlannerImportReport(3, 2, List.of(rejected), List.of(flagged)), target);

		try (Workbook workbook = WorkbookFactory.create(target.toFile())) {
			assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Resumen");
			assertThat(workbook.getSheetAt(0).getRow(3).getCell(1).getNumericCellValue()).isEqualTo(1);
			Sheet rejectedSheet = workbook.getSheet("Rechazadas");
			assertThat(rejectedSheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("Detalle");
			assertThat(rejectedSheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("COD EMPL");
			assertThat(rejectedSheet.getRow(1).getCell(5).getStringCellValue()).startsWith("Falta este dato");
			Sheet flaggedSheet = workbook.getSheet("Observaciones");
			assertThat(flaggedSheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("200");
			assertThat(flaggedSheet.getRow(1).getCell(5).getStringCellValue())
				.isEqualTo("El COD LÍDER es de Lucia Lider. Se usó el código.");
		}
	}

}
