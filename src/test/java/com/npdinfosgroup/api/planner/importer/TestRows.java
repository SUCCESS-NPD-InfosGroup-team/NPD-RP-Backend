package com.npdinfosgroup.api.planner.importer;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Construye filas del planificador para los tests, con datos inventados. Cada fila empieza completa
 * y válida; cada test cambia solo lo que necesita.
 */
final class TestRows {

	private final int rowNumber;

	private final Map<PlannerColumn, String> values = new EnumMap<>(PlannerColumn.class);

	private LocalDate hireDate = LocalDate.of(2024, 3, 1);

	private LocalDate projectEndDate = LocalDate.of(2030, 12, 31);

	private TestRows(int rowNumber, String code) {
		this.rowNumber = rowNumber;
		values.put(PlannerColumn.CLIENT, "ACME");
		values.put(PlannerColumn.PROJECT, "ACME-WEB");
		values.put(PlannerColumn.LEADER_NAME, "LUCIA LIDER");
		values.put(PlannerColumn.LEADER_CODE, "900");
		if (code != null) {
			values.put(PlannerColumn.EMPLOYEE_CODE, code);
		}
		values.put(PlannerColumn.FULL_NAME, "Persona " + code);
		values.put(PlannerColumn.JOB_TITLE, "Analista De Pruebas");
		values.put(PlannerColumn.JOB_POSITION, "ANALISTA DE PRUEBAS JR.");
		values.put(PlannerColumn.CLIENT_ROLE, "QA FUNCIONAL");
		values.put(PlannerColumn.LEVEL, "3");
		values.put(PlannerColumn.HIRE_DATE, "2024-03-01");
		values.put(PlannerColumn.BUSINESS_UNIT, "IFP");
		values.put(PlannerColumn.GENDER, "F");
		values.put(PlannerColumn.EQUIPMENT_PROVIDER, "INFOS");
		values.put(PlannerColumn.PROJECT_END_DATE, "2030-12-31");
		values.put(PlannerColumn.PROJECT_END_MONTH, "DICIEMBRE 2030");
		values.put(PlannerColumn.STATUS, "Activo-Proyecto Asignado ");
		values.put(PlannerColumn.TALENT_REVIEW, "N/A");
		values.put(PlannerColumn.EMAIL, "persona" + code + "@example.com");
		values.put(PlannerColumn.CLIENT_CONTACT, "contacto@acme.example");
		values.put(PlannerColumn.WORK_MODALITY, "Hibrido - 2 Dias");
	}

	static TestRows row(int rowNumber, String code) {
		return new TestRows(rowNumber, code);
	}

	TestRows with(PlannerColumn column, String value) {
		if (value == null) {
			values.remove(column);
		}
		else {
			values.put(column, value);
		}
		return this;
	}

	TestRows hireDate(LocalDate date) {
		this.hireDate = date;
		return with(PlannerColumn.HIRE_DATE, date == null ? null : date.toString());
	}

	TestRows projectEndDate(LocalDate date, String text) {
		this.projectEndDate = date;
		return with(PlannerColumn.PROJECT_END_DATE, text);
	}

	PlannerRow build() {
		return new PlannerRow(rowNumber, values, hireDate, projectEndDate);
	}

}
