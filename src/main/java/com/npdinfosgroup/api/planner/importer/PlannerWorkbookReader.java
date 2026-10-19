package com.npdinfosgroup.api.planner.importer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/**
 * Lee las filas de la hoja {@code PLANIFICADOR} del Excel del cliente.
 * <p>
 * Busca la fila de encabezados entre las primeras filas (en el Excel actual es la 2) y ubica cada
 * columna por su nombre, no por su posición, para tolerar columnas nuevas o movidas. Las filas
 * vacías se ignoran.
 */
@Component
class PlannerWorkbookReader {

	static final String SHEET_NAME = "PLANIFICADOR";

	private static final int HEADER_SEARCH_ROWS = 10;

	/** Columnas sin las que el import no tiene sentido; si falta alguna, se detiene. */
	private static final Set<PlannerColumn> REQUIRED_COLUMNS = EnumSet.of(PlannerColumn.EMPLOYEE_CODE,
			PlannerColumn.FULL_NAME, PlannerColumn.HIRE_DATE, PlannerColumn.GENDER, PlannerColumn.JOB_POSITION,
			PlannerColumn.STATUS);

	/**
	 * Lee el archivo y devuelve sus filas con datos.
	 *
	 * @param file ruta del Excel ({@code .xlsx})
	 * @return las filas con al menos una celda con datos, en el orden del archivo
	 * @throws IOException si el archivo no existe o no es un Excel válido
	 * @throws IllegalStateException si no se encuentra la fila de encabezados o falta una columna
	 *         obligatoria
	 */
	List<PlannerRow> read(Path file) throws IOException {
		try (Workbook workbook = WorkbookFactory.create(file.toFile(), null, true)) {
			Sheet sheet = workbook.getSheet(SHEET_NAME);
			return readSheet(sheet != null ? sheet : workbook.getSheetAt(0));
		}
	}

	private static List<PlannerRow> readSheet(Sheet sheet) {
		Row header = findHeader(sheet);
		Map<PlannerColumn, Integer> columns = mapColumns(header);
		List<PlannerRow> rows = new ArrayList<>();
		for (int i = header.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
			Row row = sheet.getRow(i);
			if (row != null) {
				PlannerRow plannerRow = toPlannerRow(row, columns);
				if (!plannerRow.values().isEmpty()) {
					rows.add(plannerRow);
				}
			}
		}
		return rows;
	}

	private static Row findHeader(Sheet sheet) {
		for (int i = sheet.getFirstRowNum(); i < sheet.getFirstRowNum() + HEADER_SEARCH_ROWS; i++) {
			Row row = sheet.getRow(i);
			if (row != null) {
				for (Cell cell : row) {
					if (PlannerColumn.EMPLOYEE_CODE.matches(text(cell))) {
						return row;
					}
				}
			}
		}
		throw new IllegalStateException("Header row not found: no column starts with 'COD EMPL' in sheet "
				+ sheet.getSheetName());
	}

	private static Map<PlannerColumn, Integer> mapColumns(Row header) {
		Map<PlannerColumn, Integer> columns = new EnumMap<>(PlannerColumn.class);
		for (Cell cell : header) {
			String title = text(cell);
			for (PlannerColumn column : PlannerColumn.values()) {
				if (column.matches(title)) {
					columns.putIfAbsent(column, cell.getColumnIndex());
				}
			}
		}
		Set<PlannerColumn> missing = EnumSet.copyOf(REQUIRED_COLUMNS);
		missing.removeAll(columns.keySet());
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Required columns missing in the planner: " + missing);
		}
		return columns;
	}

	private static PlannerRow toPlannerRow(Row row, Map<PlannerColumn, Integer> columns) {
		Map<PlannerColumn, String> values = new EnumMap<>(PlannerColumn.class);
		columns.forEach((column, index) -> {
			String text = text(row.getCell(index));
			if (text != null && !text.isBlank()) {
				values.put(column, text);
			}
		});
		return new PlannerRow(row.getRowNum() + 1, values, date(row, columns.get(PlannerColumn.HIRE_DATE)),
				date(row, columns.get(PlannerColumn.PROJECT_END_DATE)));
	}

	private static LocalDate date(Row row, Integer index) {
		Cell cell = index == null ? null : row.getCell(index);
		if (cell != null && type(cell) == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
			return cell.getLocalDateTimeCellValue().toLocalDate();
		}
		return null;
	}

	private static String text(Cell cell) {
		if (cell == null) {
			return null;
		}
		return switch (type(cell)) {
			case STRING -> cell.getStringCellValue();
			case NUMERIC -> DateUtil.isCellDateFormatted(cell)
					? cell.getLocalDateTimeCellValue().toLocalDate().toString()
					: BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
			case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
			default -> null;
		};
	}

	/** Tipo del valor de la celda; en las fórmulas, el de su último resultado guardado. */
	private static CellType type(Cell cell) {
		return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
	}

}
