package com.npdinfosgroup.api.planner.persistence.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read queries of the resource record: catalog checks before saving and the full record with the
 * names of every catalog.
 * <p>
 * Uses plain SQL so the record comes from one query, with the situation that {@code resource_v}
 * already calculates, instead of loading each catalog through JPA.
 */
@Repository
public class ResourceRepository {

	/** Catalogs an employee points to. */
	public enum Catalog {

		GENDER("SELECT active FROM gender WHERE id = :id"),
		JOB_TITLE("SELECT active FROM job_title WHERE id = :id"),
		JOB_POSITION("SELECT active FROM job_position WHERE id = :id"),
		BUSINESS_UNIT("SELECT active FROM business_unit WHERE id = :id"),
		WORK_MODALITY("SELECT active FROM work_modality WHERE id = :id"),
		TALENT_REVIEW("SELECT active FROM talent_review WHERE id = :id"),
		TERMINATION_REASON("SELECT active FROM termination_reason WHERE id = :id"),
		CLIENT_ROLE("SELECT active FROM client_role WHERE id = :id"),
		EQUIPMENT_PROVIDER("SELECT active FROM equipment_provider WHERE id = :id");

		private final String findActive;

		Catalog(String findActive) {
			this.findActive = findActive;
		}

	}

	/**
	 * A related record: its id and the name the user sees.
	 *
	 * @param id identifier
	 * @param name display name
	 */
	public record Ref(long id, String name) {
	}

	/**
	 * A resource with the names of everything it points to.
	 *
	 * @param id employee id
	 * @param code employee number
	 * @param fullName name
	 * @param email e-mail, if any
	 * @param jobTitle job title (PUESTO), if any
	 * @param jobPosition position at Infosgroup
	 * @param level level, if any
	 * @param businessUnit business unit (UEN), if any
	 * @param workModality work modality, if any
	 * @param workModalityActive whether the work modality is active; {@code false} for "No especificado"
	 * @param gender gender
	 * @param talentReview talent review result, if any
	 * @param hireDate contract start date
	 * @param probationEndDate day 90 after the contract start
	 * @param statusCode status code (ACTIVO, LICENCIA, BAJA)
	 * @param statusName status name
	 * @param situation situation calculated by {@code resource_v}
	 * @param client client of the project, if any
	 * @param project current project, if any
	 * @param projectActive whether the project is active; {@code false} for "No especificado"
	 * @param contractEndDate contract end of the project with the client, if any
	 * @param leader NPD lead, if any
	 * @param clientRole role at the client, if any
	 * @param equipmentProvider laptop provider, if any
	 * @param projectEndDate end date on the project; {@code null} = indefinite
	 * @param clientContactEmail contact at the client, if any
	 * @param terminationDate termination date, if any
	 * @param terminationReason termination reason, if any
	 * @param version optimistic locking version
	 * @param updatedAt last change
	 */
	public record ResourceRow(long id, String code, String fullName, String email, Ref jobTitle, Ref jobPosition,
			Short level, Ref businessUnit, Ref workModality, boolean workModalityActive, Ref gender,
			Ref talentReview, LocalDate hireDate, LocalDate probationEndDate, String statusCode, String statusName,
			String situation, Ref client, Ref project, boolean projectActive, LocalDate contractEndDate, Ref leader,
			Ref clientRole, Ref equipmentProvider, LocalDate projectEndDate, String clientContactEmail,
			LocalDate terminationDate, Ref terminationReason, int version, Instant updatedAt) {
	}

	private static final String FIND_RESOURCE = """
			SELECT e.id, e.code, e.full_name, e.email,
			       e.job_title_id, jt.name AS job_title_name,
			       e.job_position_id, jp.name AS job_position_name,
			       e.level,
			       e.business_unit_id, bu.name AS business_unit_name,
			       e.work_modality_id, wm.name AS work_modality_name, coalesce(wm.active, true) AS work_modality_active,
			       e.gender_id, g.name AS gender_name,
			       e.talent_review_id, tv.name AS talent_review_name,
			       e.hire_date, e.probation_end_date,
			       rs.code AS status_code, rs.name AS status_name,
			       r.assignment_situation,
			       p.client_id, c.name AS client_name,
			       e.project_id, p.name AS project_name, coalesce(p.active, true) AS project_active,
			       p.contract_end_date,
			       e.leader_id, l.full_name AS leader_name,
			       e.client_role_id, cr.name AS client_role_name,
			       e.equipment_provider_id, ep.name AS equipment_provider_name,
			       e.project_end_date, e.client_contact_email,
			       e.termination_date, e.termination_reason_id, tr.name AS termination_reason_name,
			       e.version, e.updated_at
			FROM employee e
			JOIN resource_v r                ON r.employee_id = e.id
			JOIN resource_status rs          ON rs.id = e.status_id
			JOIN gender g                    ON g.id = e.gender_id
			JOIN job_position jp             ON jp.id = e.job_position_id
			LEFT JOIN job_title jt           ON jt.id = e.job_title_id
			LEFT JOIN business_unit bu       ON bu.id = e.business_unit_id
			LEFT JOIN work_modality wm       ON wm.id = e.work_modality_id
			LEFT JOIN talent_review tv       ON tv.id = e.talent_review_id
			LEFT JOIN project p              ON p.id = e.project_id
			LEFT JOIN client c               ON c.id = p.client_id
			LEFT JOIN employee l             ON l.id = e.leader_id
			LEFT JOIN client_role cr         ON cr.id = e.client_role_id
			LEFT JOIN equipment_provider ep  ON ep.id = e.equipment_provider_id
			LEFT JOIN termination_reason tr  ON tr.id = e.termination_reason_id
			WHERE e.id = :id""";

	private final JdbcClient jdbc;

	/**
	 * Creates the repository.
	 *
	 * @param jdbc Spring JDBC client
	 */
	public ResourceRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Whether a catalog value is active.
	 *
	 * @param catalog catalog
	 * @param id id of the value
	 * @return {@code true} if active, {@code false} if inactive ("No especificado"), empty if it does
	 *         not exist
	 */
	public Optional<Boolean> findActive(Catalog catalog, long id) {
		return jdbc.sql(catalog.findActive).param("id", id).query(Boolean.class).optional();
	}

	/**
	 * Id of an active employment status.
	 *
	 * @param code status code (ACTIVO, LICENCIA, BAJA)
	 * @return its id, or empty if there is no active status with that code
	 */
	public Optional<Short> findStatusId(String code) {
		return jdbc.sql("SELECT id FROM resource_status WHERE code = :code AND active")
			.param("code", code)
			.query(Short.class)
			.optional();
	}

	/**
	 * Whether a project is active.
	 *
	 * @param projectId project id
	 * @return {@code true} if active, {@code false} if inactive, empty if it does not exist
	 */
	public Optional<Boolean> findProjectActive(long projectId) {
		return jdbc.sql("SELECT active FROM project WHERE id = :id").param("id", projectId).query(Boolean.class).optional();
	}

	/**
	 * Status code of an employee, to check that a chosen NPD lead still works at Infosgroup.
	 *
	 * @param employeeId employee id
	 * @return the status code, or empty if the employee does not exist
	 */
	public Optional<String> findStatusCode(long employeeId) {
		return jdbc.sql("""
				SELECT rs.code FROM employee e JOIN resource_status rs ON rs.id = e.status_id WHERE e.id = :id""")
			.param("id", employeeId)
			.query(String.class)
			.optional();
	}

	/**
	 * A resource with the names of everything it points to.
	 *
	 * @param id employee id
	 * @return the record, or empty if it does not exist
	 */
	public Optional<ResourceRow> findResource(long id) {
		return jdbc.sql(FIND_RESOURCE).param("id", id).query((rs, rowNum) -> toRow(rs)).optional();
	}

	private static ResourceRow toRow(ResultSet rs) throws SQLException {
		return new ResourceRow(rs.getLong("id"), rs.getString("code"), rs.getString("full_name"),
				rs.getString("email"), ref(rs, "job_title_id", "job_title_name"),
				ref(rs, "job_position_id", "job_position_name"), rs.getObject("level", Short.class),
				ref(rs, "business_unit_id", "business_unit_name"), ref(rs, "work_modality_id", "work_modality_name"),
				rs.getBoolean("work_modality_active"), ref(rs, "gender_id", "gender_name"),
				ref(rs, "talent_review_id", "talent_review_name"), rs.getObject("hire_date", LocalDate.class),
				rs.getObject("probation_end_date", LocalDate.class), rs.getString("status_code"),
				rs.getString("status_name"), rs.getString("assignment_situation"), ref(rs, "client_id", "client_name"),
				ref(rs, "project_id", "project_name"), rs.getBoolean("project_active"),
				rs.getObject("contract_end_date", LocalDate.class), ref(rs, "leader_id", "leader_name"),
				ref(rs, "client_role_id", "client_role_name"),
				ref(rs, "equipment_provider_id", "equipment_provider_name"),
				rs.getObject("project_end_date", LocalDate.class), rs.getString("client_contact_email"),
				rs.getObject("termination_date", LocalDate.class),
				ref(rs, "termination_reason_id", "termination_reason_name"), rs.getInt("version"),
				rs.getObject("updated_at", OffsetDateTime.class).toInstant());
	}

	private static Ref ref(ResultSet rs, String idColumn, String nameColumn) throws SQLException {
		long id = rs.getLong(idColumn);
		return rs.wasNull() ? null : new Ref(id, rs.getString(nameColumn));
	}

}
