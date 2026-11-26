package com.npdinfosgroup.api.planner.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.UpdateProjectCommand;
import com.npdinfosgroup.api.planner.dto.request.UpdateProjectRequest;
import com.npdinfosgroup.api.planner.dto.response.UpdateProjectResponse;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.entity.Project;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.planner.service.validator.ProjectRequestValidator;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes a project (HU-14): its name and client contract end. Only active projects are edited.
 * <p>
 * When the contract end changes, the people of the team whose end date was the old contract end move
 * with it (CA-04), in the same transaction: either the project and its people change together, or
 * nothing changes.
 * <p>
 * Like "Editar recurso", the form sends the {@code version} it read, so two administrators editing the
 * same project never overwrite each other without knowing it (409). Every change is audited per field.
 */
@Service
public class ProjectUpdateService {

	/** Entity type of the project rows in the audit log, the same one "Crear proyecto" uses. */
	static final String AUDIT_ENTITY = "project";

	private static final Logger log = LoggerFactory.getLogger(ProjectUpdateService.class);

	private static final String UPDATE = "UPDATE";

	/** Audited field of a person's end date, the same key "Editar recurso" uses. */
	private static final String END_DATE_FIELD = "assignment.endDate";

	private final ProjectRepository projects;

	private final EmployeeRepository employees;

	private final ProjectRequestValidator validator;

	private final ProjectQueryService queries;

	private final AuditService audit;

	/**
	 * Creates the service.
	 *
	 * @param projects reads and saves the project
	 * @param employees moves the people with the contract end
	 * @param validator rules of the project form
	 * @param queries builds the answer with the figures of the project
	 * @param audit records each change
	 */
	ProjectUpdateService(ProjectRepository projects, EmployeeRepository employees, ProjectRequestValidator validator,
			ProjectQueryService queries, AuditService audit) {
		this.projects = projects;
		this.employees = employees;
		this.validator = validator;
		this.queries = queries;
		this.audit = audit;
	}

	/**
	 * Edits the name and the client contract end of an active project, moving the people whose end date
	 * was the old contract end. The client never changes.
	 *
	 * @param command the project id, its new values with the version that was read, and who edits it
	 * @return the saved project, with its new version, and how many people moved their end date
	 * @throws NotFoundException if the project does not exist
	 * @throws ApiException {@link ErrorCode#PROJECT_INACTIVE} if it is inactive,
	 *         {@link ErrorCode#CONCURRENT_MODIFICATION} if someone saved it after the version that was read,
	 *         or {@link ErrorCode#PROJECT_NAME_TAKEN} if another project of the client has the name
	 */
	@Transactional
	public UpdateProjectResponse update(UpdateProjectCommand command) {
		Project project = projects.findById(command.id()).orElseThrow(() -> new NotFoundException(command.id()));
		if (!project.isActive()) {
			throw new ApiException(ErrorCode.PROJECT_INACTIVE, List.of(project.getName()), Map.of());
		}
		UpdateProjectRequest request = command.project();
		validator.validateUpdate(project, request);
		checkVersion(project, request.version());
		String name = request.name().strip();
		validator.checkNameAvailable(project, name);

		Map<String, String> before = values(project);
		LocalDate previousEnd = project.getContractEndDate();
		project.setName(name);
		project.setContractEndDate(request.contractEndDate());
		save(project);
		long moved = moveMembersEndDate(project.getId(), previousEnd, project.getContractEndDate(), command.actor());
		int changes = audit.recordChanges(AUDIT_ENTITY, project.getId(), before, values(project), command.actor());
		if (changes > 0) {
			log.info("Project {} edited", project.getId());
		}
		return new UpdateProjectResponse(queries.get(project.getId()), moved);
	}

	/**
	 * Moves to the new contract end the people of the team (Baja excluded) whose end date was the old one,
	 * each with its own audit row. Nobody moves when the project had no contract end or it was removed,
	 * so nobody becomes indefinite without someone choosing it.
	 *
	 * @param projectId project id
	 * @param previousEnd contract end before the edit
	 * @param newEnd contract end after the edit
	 * @param actor who edits the project
	 * @return how many people moved
	 * @throws ApiException {@link ErrorCode#CONCURRENT_MODIFICATION} if one of them was saved by someone
	 *         else at the same moment; the whole edit is rolled back
	 */
	long moveMembersEndDate(long projectId, LocalDate previousEnd, LocalDate newEnd, String actor) {
		if (previousEnd == null || newEnd == null || previousEnd.equals(newEnd)) {
			return 0;
		}
		List<Employee> members = employees.findByProjectIdAndProjectEndDateAndStatus_CodeNot(projectId, previousEnd,
				ResourceRequestValidator.TERMINATED);
		members.forEach(member -> member.setProjectEndDate(newEnd));
		try {
			employees.saveAllAndFlush(members);
		}
		catch (OptimisticLockingFailureException ex) {
			throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(), Map.of());
		}
		members.forEach(member -> audit.recordChanges(ResourceQueryService.AUDIT_ENTITY, member.getId(),
				Map.of(END_DATE_FIELD, previousEnd.toString()), Map.of(END_DATE_FIELD, newEnd.toString()), actor));
		return members.size();
	}

	/**
	 * Rejects a form read before the last save of the project.
	 *
	 * @param project the project as it is saved now
	 * @param version the version the form read
	 * @throws ApiException {@link ErrorCode#CONCURRENT_MODIFICATION} if they differ
	 */
	void checkVersion(Project project, Integer version) {
		if (!Objects.equals(version, project.getVersion())) {
			throw concurrentModification(project);
		}
	}

	/**
	 * Writes the project now, so a taken name or a concurrent save is reported here and not at commit.
	 *
	 * @param project the changed project
	 */
	void save(Project project) {
		try {
			projects.saveAndFlush(project);
		}
		catch (OptimisticLockingFailureException ex) {
			// Someone saved the project between the version check and this write
			throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(), Map.of());
		}
		catch (DataIntegrityViolationException ex) {
			// Someone used the same name in the client between the check and this write
			throw new ApiException(ErrorCode.PROJECT_NAME_TAKEN,
					List.of(project.getClient().getName(), project.getName()), Map.of());
		}
	}

	/**
	 * The 409 of a stale form, with what the frontend needs to tell the user: the current version, when
	 * the project was saved, who saved it and which fields they changed.
	 *
	 * @param current the project as it is saved now
	 * @return the exception to throw
	 */
	ApiException concurrentModification(Project current) {
		Map<String, Object> properties = new HashMap<>();
		properties.put("currentVersion", current.getVersion());
		if (current.getUpdatedAt() != null) {
			properties.put("updatedAt", current.getUpdatedAt());
		}
		audit.findLastChange(AUDIT_ENTITY, current.getId()).ifPresent(change -> {
			properties.put("updatedBy", change.actor());
			if (UPDATE.equals(change.action())) {
				properties.put("updatedFields", change.fields());
			}
		});
		return new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(), properties);
	}

	/**
	 * The audited fields of a project, with the same keys as its creation.
	 *
	 * @param project the project
	 * @return field name to the value the user sees
	 */
	private static Map<String, String> values(Project project) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("name", project.getName());
		values.put("contractEndDate", Objects.toString(project.getContractEndDate(), null));
		return values;
	}

}
