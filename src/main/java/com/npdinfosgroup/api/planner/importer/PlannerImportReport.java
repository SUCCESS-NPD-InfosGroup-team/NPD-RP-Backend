package com.npdinfosgroup.api.planner.importer;

import java.util.List;

/**
 * Resultado de un import del planificador.
 *
 * @param rowsRead filas con datos que tenía el Excel
 * @param imported empleados creados o actualizados
 * @param rejected motivos por los que no se importaron algunas filas
 * @param flagged valores que se importaron con una corrección y conviene revisar
 */
public record PlannerImportReport(int rowsRead, int imported, List<ImportIssue> rejected, List<ImportIssue> flagged) {

	public PlannerImportReport {
		rejected = List.copyOf(rejected);
		flagged = List.copyOf(flagged);
	}

	/**
	 * Filas que no se importaron.
	 *
	 * @return la cantidad de filas leídas menos las importadas
	 */
	public int rejectedRows() {
		return rowsRead - imported;
	}

}
