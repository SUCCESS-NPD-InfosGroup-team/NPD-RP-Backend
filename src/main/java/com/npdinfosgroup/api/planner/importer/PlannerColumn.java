package com.npdinfosgroup.api.planner.importer;

/**
 * Columns of the {@code PLANIFICADOR} sheet that the import reads.
 * <p>
 * Each column is recognized by the start of its header, ignoring accents and case, because the
 * Excel adds texts such as "( obligatorio)" that change between versions of the file.
 * Calculated columns (probation, vacation) are not read: the database generates them.
 */
public enum PlannerColumn {

	CLIENT("CLIENTE", "CLIENTE"),
	PROJECT("PROYECTO ASIGNADO", "PROYECTO ASIGNADO"),
	LEADER_NAME("LIDER TLP", "LÍDER TLP"),
	LEADER_CODE("COD LIDER", "COD LÍDER"),
	EMPLOYEE_CODE("COD EMPL", "COD EMPL"),
	FULL_NAME("NOMBRE DE EMPLEADO", "NOMBRE DE EMPLEADO"),
	JOB_TITLE("PUESTO", "PUESTO"),
	JOB_POSITION("POSICION EN INFOSGROUP", "POSICIÓN EN INFOSGROUP"),
	CLIENT_ROLE("ROL ACTUAL EN CLIENTE", "ROL ACTUAL EN CLIENTE"),
	LEVEL("NIVEL", "NIVEL"),
	HIRE_DATE("FECHA DE INICIO", "FECHA DE INICIO"),
	BUSINESS_UNIT("UEN", "UEN"),
	GENDER("GENERO", "GÉNERO"),
	EQUIPMENT_PROVIDER("EQUIPO", "EQUIPO / LAPTOP"),
	PROJECT_END_DATE("FECHA FINAL PROYECTO", "FECHA FINAL PROYECTO"),
	PROJECT_END_MONTH("MES FINAL DE PROYECTO", "MES FINAL DE PROYECTO"),
	STATUS("ESTADO", "ESTADO"),
	TERMINATION_REASON("DESVINCULACION", "DESVINCULACIÓN EN INFOSGROUP"),
	TALENT_REVIEW("RESULTADO DE TALENT REVIEW", "RESULTADO DE TALENT REVIEW"),
	EMAIL("CORREO RECURSO", "CORREO RECURSO"),
	CLIENT_CONTACT("CONTACTO POR CLIENTE", "CONTACTO POR CLIENTE"),
	WORK_MODALITY("MODALIDAD LABORAL", "MODALIDAD LABORAL DEL RECURSO");

	private final String headerPrefix;

	private final String label;

	PlannerColumn(String headerPrefix, String label) {
		this.headerPrefix = headerPrefix;
		this.label = label;
	}

	/**
	 * Tells whether an Excel header belongs to this column.
	 *
	 * @param header header text as it comes in the Excel
	 * @return {@code true} if the normalized header starts with this column's prefix
	 */
	boolean matches(String header) {
		return header != null && PlannerValues.key(header).startsWith(headerPrefix);
	}

	/**
	 * Column name as users know it, for the import report.
	 *
	 * @return the column name in the Excel
	 */
	public String label() {
		return label;
	}

}
