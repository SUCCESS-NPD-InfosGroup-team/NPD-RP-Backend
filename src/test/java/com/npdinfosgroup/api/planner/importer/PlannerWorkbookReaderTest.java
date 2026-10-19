package com.npdinfosgroup.api.planner.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lectura del Excel del planificador con un archivo pequeño generado en el test, con la misma
 * estructura que el real: título en la fila 1, encabezados en la 2 y datos desde la 3.
 */
class PlannerWorkbookReaderTest {

	private static final List<String> HEADERS = List.of("CLIENTE ( obligatorio)", "COD LIDER ( obligatorio)",
			"COD EMPL( obligatorio)", "NOMBRE DE EMPLEADO ( obligatorio)", "POSICIÓN EN INFOSGROUP ( obligatorio)",
			"FECHA DE INICIO( obligatorio)", "GENERO ( obligatorio)", "MES DE PROBATORIA (( obligatorio)",
			"FECHA FINAL PROYECTO (( obligatorio)", "ESTADO ( obligatorio)", "NIVEL ( OPCIONAL) ");

	@TempDir
	Path dir;

	private final PlannerWorkbookReader reader = new PlannerWorkbookReader();

	/**
	 * Encuentra los encabezados, lee números, textos y fechas, y salta las filas vacías.
	 */
	@Test
	void readsRowsByHeaderName() throws IOException {
		Path file = write(PlannerWorkbookReader.SHEET_NAME, HEADERS, workbook -> {
			Sheet sheet = workbook.getSheet(PlannerWorkbookReader.SHEET_NAME);
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
			Row first = sheet.createRow(2);
			first.createCell(0).setCellValue("ACME");
			first.createCell(1).setCellValue(900);
			first.createCell(2).setCellValue(34);
			first.createCell(3).setCellValue("Ana  Prueba");
			first.createCell(4).setCellValue("ANALISTA JR.");
			first.createCell(5).setCellValue(LocalDate.of(2024, 3, 1));
			first.getCell(5).setCellStyle(dateStyle);
			first.createCell(6).setCellValue("F");
			first.createCell(7).setCellFormula("F3+90");
			first.createCell(8).setCellValue("INDEFINIDO");
			first.createCell(9).setCellValue("Activo");
			first.createCell(10).setCellValue(2.5);
			sheet.createRow(3).createCell(0).setCellValue("   ");
			Row second = sheet.createRow(4);
			second.createCell(2).setCellValue("SP11");
			second.createCell(8).setCellValue(LocalDate.of(2026, 12, 31));
			second.getCell(8).setCellStyle(dateStyle);
			second.createCell(9).setCellValue(true);
		});

		List<PlannerRow> rows = reader.read(file);

		assertThat(rows).hasSize(2);
		PlannerRow first = rows.get(0);
		assertThat(first.rowNumber()).isEqualTo(3);
		assertThat(first.raw(PlannerColumn.EMPLOYEE_CODE)).isEqualTo("34");
		assertThat(first.raw(PlannerColumn.LEADER_CODE)).isEqualTo("900");
		assertThat(first.get(PlannerColumn.FULL_NAME)).isEqualTo("Ana Prueba");
		assertThat(first.raw(PlannerColumn.LEVEL)).isEqualTo("2.5");
		assertThat(first.hireDate()).isEqualTo(LocalDate.of(2024, 3, 1));
		assertThat(first.raw(PlannerColumn.HIRE_DATE)).isEqualTo("2024-03-01");
		assertThat(first.projectEndDate()).isNull();
		assertThat(first.raw(PlannerColumn.PROJECT_END_DATE)).isEqualTo("INDEFINIDO");
		PlannerRow second = rows.get(1);
		assertThat(second.rowNumber()).isEqualTo(5);
		assertThat(second.raw(PlannerColumn.EMPLOYEE_CODE)).isEqualTo("SP11");
		assertThat(second.projectEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(second.raw(PlannerColumn.STATUS)).isEqualTo("true");
		assertThat(second.hireDate()).isNull();
	}

	/**
	 * Si no hay una hoja PLANIFICADOR, lee la primera hoja.
	 */
	@Test
	void fallsBackToFirstSheet() throws IOException {
		Path file = write("Hoja1", HEADERS,
				workbook -> workbook.getSheet("Hoja1").createRow(2).createCell(2).setCellValue("100"));

		assertThat(reader.read(file)).extracting(row -> row.raw(PlannerColumn.EMPLOYEE_CODE)).containsExactly("100");
	}

	/**
	 * Sin la fila de encabezados, el import se detiene.
	 */
	@Test
	void failsWithoutHeaderRow() throws IOException {
		Path file = write(PlannerWorkbookReader.SHEET_NAME, List.of("OTRA COSA"), workbook -> {
		});

		assertThatThrownBy(() -> reader.read(file)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Header row not found");
	}

	/**
	 * Sin una columna obligatoria, el import se detiene y dice cuál falta.
	 */
	@Test
	void failsWhenRequiredColumnIsMissing() throws IOException {
		Path file = write(PlannerWorkbookReader.SHEET_NAME, List.of("COD EMPL", "NOMBRE DE EMPLEADO"), workbook -> {
		});

		assertThatThrownBy(() -> reader.read(file)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("HIRE_DATE");
	}

	private Path write(String sheetName, List<String> headers, WorkbookContent content) throws IOException {
		Path file = dir.resolve("planner.xlsx");
		try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
			Sheet sheet = workbook.createSheet(sheetName);
			sheet.createRow(0).createCell(1).setCellValue("DATA_INFOSWEB");
			Row header = sheet.createRow(1);
			for (int i = 0; i < headers.size(); i++) {
				header.createCell(i).setCellValue(headers.get(i));
			}
			content.fill(workbook);
			workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
			workbook.write(out);
		}
		return file;
	}

	@FunctionalInterface
	private interface WorkbookContent {

		void fill(XSSFWorkbook workbook);

	}

}
