package com.npdinfosgroup.api.planner.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.UpdateResourceCommand;
import com.npdinfosgroup.api.planner.dto.request.UpdateResourceRequest;
import com.npdinfosgroup.api.planner.dto.response.ResourceResponse;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.service.mapper.ResourceMapper;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edits the record of a resource (HU-02) and audits every field that changed.
 * <p>
 * The edit replaces the whole record with the one the form sends. It is saved only if nobody saved the
 * record after the user read it (its {@code version}), so two people editing at once never overwrite
 * each other silently. Saving and auditing happen in one transaction: either both are saved or nothing
 * is.
 */
@Service
public class ResourceUpdateService {

	private static final Logger log = LoggerFactory.getLogger(ResourceUpdateService.class);

	/** Audit action of an edit. */
	private static final String UPDATE = "UPDATE";

	private final ResourceRequestValidator validator;

	private final EmployeeRepository employees;

	private final ResourceQueryService queries;

	private final AuditService audit;

	private final ResourceMapper mapper;

	/**
	 * Creates the service.
	 *
	 * @param validator checks the request
	 * @param employees loads and saves the employee
	 * @param queries gives the situation of the saved record
	 * @param audit records the changes and who made the last one
	 * @param mapper builds the response and the audited values
	 */
	ResourceUpdateService(ResourceRequestValidator validator, EmployeeRepository employees,
			ResourceQueryService queries, AuditService audit, ResourceMapper mapper) {
		this.validator = validator;
		this.employees = employees;
		this.queries = queries;
		this.audit = audit;
		this.mapper = mapper;
	}

	/**
	 * Edits a resource.
	 * <p>
	 * Changing the project moves the person to it: there is one project at a time, and the audit keeps
	 * the previous and the new one. On Baja the person leaves their project. An edit that changes
	 * nothing saves nothing and leaves no audit rows.
	 *
	 * @param command the resource id, its new values with the version that was read, and who edits it
	 * @return the saved record, with its new version
	 * @throws NotFoundException if the resource does not exist
	 * @throws ApiException {@link ErrorCode#CONCURRENT_MODIFICATION} if someone saved the record after
	 *         the version that was read, or {@link ErrorCode#EMPLOYEE_NUMBER_TAKEN} if another resource
	 *         has the employee number
	 * @throws ValidationException with every invalid field
	 */
	@Transactional
	public ResourceResponse update(UpdateResourceCommand command) {
		long id = command.id();
		UpdateResourceRequest request = command.resource();
		Employee employee = employees.findDetailedById(id).orElseThrow(() -> new NotFoundException(id));
		// A stale form is reported before its fields: they may be wrong only because the record changed
		if (request.version() != null && !request.version().equals(employee.getVersion())) {
			throw concurrentModification(employee);
		}
		short statusId = validator.validateUpdate(id, request);
		Map<String, String> before = mapper.auditValues(employee);
		ResourceFields.copy(request, statusId, employee);
		save(employee);
		// Loads what the database set: the new version, generated columns and the related catalogs
		employees.refresh(employee);
		int changes = audit.recordChanges(ResourceQueryService.AUDIT_ENTITY, id, before, mapper.auditValues(employee),
				command.actor());
		String updatedBy = changes > 0 ? command.actor()
				: audit.findLastActor(ResourceQueryService.AUDIT_ENTITY, id).orElse(null);
		log.info("Resource {} updated, {} field(s) changed", id, changes);
		return mapper.toResponse(employee, queries.situationOf(id), updatedBy);
	}

	/**
	 * Writes the changes. Hibernate only updates the row if some value changed, and only if its version
	 * is still the one that was loaded.
	 */
	private void save(Employee employee) {
		try {
			employees.flush();
		}
		catch (OptimisticLockingFailureException ex) {
			// Someone saved the record between the version check and this write
			throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(), Map.of());
		}
		catch (DataIntegrityViolationException ex) {
			// Someone registered the same number between the validation and this write
			if (ResourceFields.isCodeConflict(ex)) {
				throw new ApiException(ErrorCode.EMPLOYEE_NUMBER_TAKEN, List.of(employee.getCode(), ""), Map.of());
			}
			throw ex;
		}
	}

	/**
	 * The 409 of a stale form, with what the frontend needs to tell the user: the current version, who
	 * saved it, when, and which fields they changed.
	 */
	private ApiException concurrentModification(Employee current) {
		Map<String, Object> properties = new HashMap<>();
		properties.put("currentVersion", current.getVersion());
		if (current.getUpdatedAt() != null) {
			properties.put("updatedAt", current.getUpdatedAt());
		}
		audit.findLastChange(ResourceQueryService.AUDIT_ENTITY, current.getId()).ifPresent(change -> {
			properties.put("updatedBy", change.actor());
			// A registration lists every initial value, which says nothing about what changed
			if (UPDATE.equals(change.action())) {
				properties.put("updatedFields", change.fields());
			}
		});
		return new ApiException(ErrorCode.CONCURRENT_MODIFICATION, List.of(), properties);
	}

}
