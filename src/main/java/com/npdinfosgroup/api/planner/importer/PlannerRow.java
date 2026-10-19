package com.npdinfosgroup.api.planner.importer;

import java.time.LocalDate;
import java.util.Map;

/**
 * Una fila del planificador tal como se leyó del Excel, antes de limpiarla.
 *
 * @param rowNumber número de la fila en el Excel (empieza en 1), para el reporte
 * @param values texto original de cada columna; las columnas vacías pueden faltar
 * @param hireDate FECHA DE INICIO, si la celda es una fecha
 * @param projectEndDate FECHA FINAL PROYECTO, si la celda es una fecha ("INDEFINIDO" queda en
 *        {@code null})
 */
public record PlannerRow(int rowNumber, Map<PlannerColumn, String> values, LocalDate hireDate,
		LocalDate projectEndDate) {

	public PlannerRow {
		values = Map.copyOf(values);
	}

	/**
	 * Texto original de una columna.
	 *
	 * @param column columna a leer
	 * @return el texto tal como viene en el Excel, o {@code null} si la celda está vacía
	 */
	public String raw(PlannerColumn column) {
		return values.get(column);
	}

	/**
	 * Texto limpio de una columna, según {@link PlannerValues#clean(String)}.
	 *
	 * @param column columna a leer
	 * @return el texto limpio, o {@code null} si la celda no tiene datos
	 */
	public String get(PlannerColumn column) {
		return PlannerValues.clean(values.get(column));
	}

}
