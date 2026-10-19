package com.npdinfosgroup.api.planner.persistence.repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.request.ImportEmployeeCommand;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acceso a datos del import del planificador.
 * <p>
 * Usa SQL directo en lugar de entidades JPA porque el import necesita "insertar o actualizar"
 * ({@code ON CONFLICT}) para poder repetirse sin duplicar datos, y buscar los catálogos sin
 * distinguir mayúsculas, igual que sus índices únicos.
 */
@Repository
public class PlannerImportRepository {

	/** Catálogos fijos: sus valores vienen sembrados por las migraciones y el import solo los busca. */
	public enum FixedCatalog {

		RESOURCE_STATUS("SELECT id, code, name FROM resource_status"),
		GENDER("SELECT id, code, name FROM gender"),
		BUSINESS_UNIT("SELECT id, code, name FROM business_unit"),
		EQUIPMENT_PROVIDER("SELECT id, code, name FROM equipment_provider"),
		WORK_MODALITY("SELECT id, code, name FROM work_modality"),
		TALENT_REVIEW("SELECT id, code, name FROM talent_review"),
		TERMINATION_REASON("SELECT id, code, name FROM termination_reason");

		private final String selectAll;

		FixedCatalog(String selectAll) {
			this.selectAll = selectAll;
		}

	}

	/** Catálogos abiertos: el import crea los valores que todavía no existen. */
	public enum OpenCatalog {

		JOB_TITLE("SELECT id FROM job_title WHERE lower(name) = lower(:name)",
				"INSERT INTO job_title (name) VALUES (:name) RETURNING id"),
		JOB_POSITION("SELECT id FROM job_position WHERE lower(name) = lower(:name)",
				"INSERT INTO job_position (name) VALUES (:name) RETURNING id"),
		CLIENT_ROLE("SELECT id FROM client_role WHERE lower(name) = lower(:name)",
				"INSERT INTO client_role (name) VALUES (:name) RETURNING id");

		private final String select;

		private final String insert;

		OpenCatalog(String select, String insert) {
			this.select = select;
			this.insert = insert;
		}

	}

	/**
	 * Un valor de un catálogo fijo.
	 *
	 * @param id identificador
	 * @param code código estable
	 * @param name texto que ve el usuario
	 */
	public record CatalogValue(short id, String code, String name) {
	}

	/**
	 * Datos mínimos de un empleado ya guardado, para asignar líderes.
	 *
	 * @param id identificador
	 * @param fullName nombre completo
	 */
	public record EmployeeRef(long id, String fullName) {
	}

	private final JdbcClient jdbc;

	/**
	 * Crea el repositorio.
	 *
	 * @param jdbc cliente JDBC de Spring
	 */
	public PlannerImportRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Todos los valores de un catálogo fijo, activos o no.
	 *
	 * @param catalog catálogo a leer
	 * @return sus valores
	 */
	public List<CatalogValue> findAll(FixedCatalog catalog) {
		return jdbc.sql(catalog.selectAll)
			.query((rs, rowNum) -> new CatalogValue(rs.getShort("id"), rs.getString("code"), rs.getString("name")))
			.list();
	}

	/**
	 * Busca un valor de un catálogo abierto sin distinguir mayúsculas y lo crea si no existe.
	 *
	 * @param catalog catálogo
	 * @param name valor; si se crea, se guarda tal como viene
	 * @return el id del valor
	 */
	public long findOrCreate(OpenCatalog catalog, String name) {
		return jdbc.sql(catalog.select)
			.param("name", name)
			.query(Long.class)
			.optional()
			.orElseGet(() -> jdbc.sql(catalog.insert).param("name", name).query(Long.class).single());
	}

	/**
	 * Busca un cliente por nombre sin distinguir mayúsculas y lo crea si no existe.
	 *
	 * @param name nombre del cliente
	 * @param internal si es trabajo interno de Infosgroup, como NPD-ADMINISTRACION
	 * @return el id del cliente
	 */
	public long findOrCreateClient(String name, boolean internal) {
		return jdbc.sql("SELECT id FROM client WHERE lower(name) = lower(:name)")
			.param("name", name)
			.query(Long.class)
			.optional()
			.orElseGet(() -> jdbc.sql("INSERT INTO client (name, is_internal) VALUES (:name, :internal) RETURNING id")
				.param("name", name)
				.param("internal", internal)
				.query(Long.class)
				.single());
	}

	/**
	 * Busca un proyecto de un cliente por nombre sin distinguir mayúsculas y lo crea si no existe.
	 *
	 * @param clientId cliente dueño del proyecto
	 * @param name nombre del proyecto
	 * @return el id del proyecto
	 */
	public long findOrCreateProject(long clientId, String name) {
		return jdbc.sql("SELECT id FROM project WHERE client_id = :clientId AND lower(name) = lower(:name)")
			.param("clientId", clientId)
			.param("name", name)
			.query(Long.class)
			.optional()
			.orElseGet(() -> jdbc.sql("INSERT INTO project (client_id, name) VALUES (:clientId, :name) RETURNING id")
				.param("clientId", clientId)
				.param("name", name)
				.query(Long.class)
				.single());
	}

	/**
	 * Proyecto "No especificado" que crea la migración V3 para las filas sin cliente o proyecto.
	 *
	 * @return su id
	 */
	public long findNotSpecifiedProject() {
		return jdbc.sql("""
				SELECT p.id
				FROM project p
				JOIN client c ON c.id = p.client_id
				WHERE c.name = 'No especificado' AND p.name = 'No especificado'""")
			.query(Long.class)
			.single();
	}

	/**
	 * Crea el empleado o, si ya existe un empleado con ese código, reemplaza sus datos. El líder no
	 * se toca aquí: se asigna después con {@link #updateLeader(long, Long)}, cuando todos los
	 * empleados ya existen.
	 *
	 * @param command datos del empleado
	 * @return el id del empleado
	 */
	public long upsertEmployee(ImportEmployeeCommand command) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, email, gender_id, job_title_id, job_position_id, level,
				                      business_unit_id, work_modality_id, hire_date, status_id, termination_reason_id,
				                      talent_review_id, project_id, client_role_id, equipment_provider_id,
				                      client_contact_email, project_end_date)
				VALUES (:code, :fullName, :email, :genderId, :jobTitleId, :jobPositionId, :level,
				        :businessUnitId, :workModalityId, :hireDate, :statusId, :terminationReasonId,
				        :talentReviewId, :projectId, :clientRoleId, :equipmentProviderId,
				        :clientContactEmail, :projectEndDate)
				ON CONFLICT (code) DO UPDATE SET
				    full_name             = EXCLUDED.full_name,
				    email                 = EXCLUDED.email,
				    gender_id             = EXCLUDED.gender_id,
				    job_title_id          = EXCLUDED.job_title_id,
				    job_position_id       = EXCLUDED.job_position_id,
				    level                 = EXCLUDED.level,
				    business_unit_id      = EXCLUDED.business_unit_id,
				    work_modality_id      = EXCLUDED.work_modality_id,
				    hire_date             = EXCLUDED.hire_date,
				    status_id             = EXCLUDED.status_id,
				    termination_reason_id = EXCLUDED.termination_reason_id,
				    talent_review_id      = EXCLUDED.talent_review_id,
				    project_id            = EXCLUDED.project_id,
				    client_role_id        = EXCLUDED.client_role_id,
				    equipment_provider_id = EXCLUDED.equipment_provider_id,
				    client_contact_email  = EXCLUDED.client_contact_email,
				    project_end_date      = EXCLUDED.project_end_date,
				    version               = employee.version + 1
				RETURNING id""")
			.param("code", command.code())
			.param("fullName", command.fullName())
			.param("email", command.email(), Types.VARCHAR)
			.param("genderId", command.genderId())
			.param("jobTitleId", command.jobTitleId(), Types.BIGINT)
			.param("jobPositionId", command.jobPositionId())
			.param("level", command.level(), Types.SMALLINT)
			.param("businessUnitId", command.businessUnitId(), Types.SMALLINT)
			.param("workModalityId", command.workModalityId())
			.param("hireDate", command.hireDate())
			.param("statusId", command.statusId())
			.param("terminationReasonId", command.terminationReasonId(), Types.SMALLINT)
			.param("talentReviewId", command.talentReviewId(), Types.SMALLINT)
			.param("projectId", command.projectId(), Types.BIGINT)
			.param("clientRoleId", command.clientRoleId(), Types.BIGINT)
			.param("equipmentProviderId", command.equipmentProviderId(), Types.SMALLINT)
			.param("clientContactEmail", command.clientContactEmail(), Types.VARCHAR)
			.param("projectEndDate", command.projectEndDate(), Types.DATE)
			.query(Long.class)
			.single();
	}

	/**
	 * Todos los empleados guardados, por código, para resolver la columna COD LÍDER.
	 *
	 * @return el id y el nombre de cada empleado, indexados por su código
	 */
	public Map<String, EmployeeRef> findEmployeesByCode() {
		return jdbc.sql("SELECT id, code, full_name FROM employee")
			.query((rs, rowNum) -> Map.entry(rs.getString("code"), new EmployeeRef(rs.getLong("id"),
					rs.getString("full_name"))))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	/**
	 * Asigna o quita el líder de un empleado.
	 *
	 * @param employeeId empleado
	 * @param leaderId su líder, o {@code null} para dejarlo sin líder
	 */
	public void updateLeader(long employeeId, Long leaderId) {
		jdbc.sql("UPDATE employee SET leader_id = :leaderId WHERE id = :id")
			.param("leaderId", leaderId, Types.BIGINT)
			.param("id", employeeId)
			.update();
	}

}
