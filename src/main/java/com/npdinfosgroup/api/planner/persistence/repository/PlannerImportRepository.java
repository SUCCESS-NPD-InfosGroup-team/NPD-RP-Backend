package com.npdinfosgroup.api.planner.persistence.repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.request.ImportEmployeeCommand;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Data access for the planner import.
 * <p>
 * Uses plain SQL instead of JPA entities because the import needs "insert or update"
 * ({@code ON CONFLICT}) so it can run again without duplicating data, and needs to look up catalogs
 * case-insensitively, like their unique indexes.
 */
@Repository
public class PlannerImportRepository {

	/** Fixed catalogs: the migrations seed their values and the import only looks them up. */
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

	/** Open catalogs: the import creates the values that do not exist yet. */
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
	 * A value of a fixed catalog.
	 *
	 * @param id identifier
	 * @param code stable code
	 * @param name text the user sees
	 */
	public record CatalogValue(short id, String code, String name) {
	}

	/**
	 * Minimal data of a saved employee, used to assign leaders.
	 *
	 * @param id identifier
	 * @param fullName full name
	 */
	public record EmployeeRef(long id, String fullName) {
	}

	private final JdbcClient jdbc;

	/**
	 * Creates the repository.
	 *
	 * @param jdbc Spring JDBC client
	 */
	public PlannerImportRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * All values of a fixed catalog, active or not.
	 *
	 * @param catalog catalog to read
	 * @return its values
	 */
	public List<CatalogValue> findAll(FixedCatalog catalog) {
		return jdbc.sql(catalog.selectAll)
			.query((rs, rowNum) -> new CatalogValue(rs.getShort("id"), rs.getString("code"), rs.getString("name")))
			.list();
	}

	/**
	 * Finds a value of an open catalog case-insensitively and creates it if it does not exist.
	 *
	 * @param catalog catalog
	 * @param name value; when created, it is stored as given
	 * @return the id of the value
	 */
	public long findOrCreate(OpenCatalog catalog, String name) {
		return jdbc.sql(catalog.select)
			.param("name", name)
			.query(Long.class)
			.optional()
			.orElseGet(() -> jdbc.sql(catalog.insert).param("name", name).query(Long.class).single());
	}

	/**
	 * Finds a client by name case-insensitively and creates it if it does not exist.
	 *
	 * @param name client name
	 * @param internal whether it is Infosgroup internal work, such as NPD-ADMINISTRACION
	 * @return the id of the client
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
	 * Finds a client's project by name case-insensitively and creates it if it does not exist.
	 *
	 * @param clientId client that owns the project
	 * @param name project name
	 * @return the id of the project
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
	 * "No especificado" project created by migration V3 for rows without client or project.
	 *
	 * @return its id
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
	 * Creates the employee or, if an employee with that code exists, replaces its data. The leader is
	 * not set here: {@link #updateLeader(long, Long)} assigns it later, once every employee
	 * exists.
	 *
	 * @param command employee data
	 * @return the id of the employee
	 */
	public long upsertEmployee(ImportEmployeeCommand command) {
		return jdbc.sql("""
				INSERT INTO employee (code, full_name, email, gender_id, job_title_id, job_position_id, level,
				                      business_unit_id, work_modality_id, hire_date, status_id, termination_reason_id,
				                      talent_review_id, project_id, client_role_id, equipment_provider_id,
				                      client_contact_email, project_end_date, on_probation)
				VALUES (:code, :fullName, :email, :genderId, :jobTitleId, :jobPositionId, :level,
				        :businessUnitId, :workModalityId, :hireDate, :statusId, :terminationReasonId,
				        :talentReviewId, :projectId, :clientRoleId, :equipmentProviderId,
				        :clientContactEmail, :projectEndDate, :onProbation)
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
				    on_probation          = EXCLUDED.on_probation,
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
			.param("onProbation", command.onProbation())
			.query(Long.class)
			.single();
	}

	/**
	 * All saved employees by code, to resolve the COD LIDER column.
	 *
	 * @return the id and name of each employee, keyed by code
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
	 * Sets or clears the leader of an employee.
	 *
	 * @param employeeId employee
	 * @param leaderId the leader, or {@code null} to leave the employee without one
	 */
	public void updateLeader(long employeeId, Long leaderId) {
		jdbc.sql("UPDATE employee SET leader_id = :leaderId WHERE id = :id")
			.param("leaderId", leaderId, Types.BIGINT)
			.param("id", employeeId)
			.update();
	}

}
