package com.npdinfosgroup.api.planner.importer;

/**
 * Columnas de la hoja {@code PLANIFICADOR} que lee el import.
 * <p>
 * Cada columna se reconoce por el inicio de su encabezado, sin tildes ni mayúsculas, porque el
 * Excel les agrega textos como "( obligatorio)" que cambian entre versiones del archivo. Las
 * columnas calculadas (probatoria, vacaciones) no se leen: la base de datos las genera.
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
	 * Indica si un encabezado del Excel corresponde a esta columna.
	 *
	 * @param header texto del encabezado tal como viene en el Excel
	 * @return {@code true} si el encabezado, normalizado, empieza con el de esta columna
	 */
	boolean matches(String header) {
		return header != null && PlannerValues.key(header).startsWith(headerPrefix);
	}

	/**
	 * Nombre de la columna como lo conoce el usuario, para el reporte del import.
	 *
	 * @return el nombre de la columna en el Excel
	 */
	public String label() {
		return label;
	}

}
