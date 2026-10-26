package com.npdinfosgroup.api.planner.service;

import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.ItemsResponse;
import com.npdinfosgroup.api.planner.dto.response.ResourceResponse;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.entity.ResourceSituation;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ResourceSituationRepository;
import com.npdinfosgroup.api.planner.service.mapper.ResourceMapper;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the full record of a resource, as the registration and edit forms show it.
 */
@Service
public class ResourceQueryService {

	/** Name of the employee table in the audit log. */
	static final String AUDIT_ENTITY = "employee";

	private final EmployeeRepository employees;

	private final ResourceSituationRepository situations;

	private final AuditService audit;

	private final ResourceMapper mapper;

	/**
	 * Creates the service.
	 *
	 * @param employees the stored resources
	 * @param situations the situation calculated for each resource
	 * @param audit gives who changed the resource last
	 * @param mapper builds the response
	 */
	ResourceQueryService(EmployeeRepository employees, ResourceSituationRepository situations, AuditService audit,
			ResourceMapper mapper) {
		this.employees = employees;
		this.situations = situations;
		this.audit = audit;
		this.mapper = mapper;
	}

	/**
	 * The full record of a resource.
	 *
	 * @param id employee id
	 * @return the record, with its calculated situation and who changed it last
	 * @throws NotFoundException if the resource does not exist
	 */
	@Transactional(readOnly = true)
	public ResourceResponse get(long id) {
		Employee employee = employees.findDetailedById(id).orElseThrow(() -> new NotFoundException(id));
		return mapper.toResponse(employee, situationOf(id), audit.findLastActor(AUDIT_ENTITY, id).orElse(null));
	}

	/**
	 * The NPD leads that can be chosen for a collaborator: employees who lead at least one and are not
	 * on Baja, by name.
	 *
	 * @return the leads
	 */
	@Transactional(readOnly = true)
	public ItemsResponse<IdName> findNpdLeads() {
		return new ItemsResponse<>(employees.findLeadsExcludingStatus(ResourceRequestValidator.TERMINATED)
			.stream()
			.map(lead -> new IdName(lead.getId(), lead.getFullName()))
			.toList());
	}

	/**
	 * The situation of a resource (Asignado, Pendiente de confirmación, Sin proyecto), as
	 * {@code resource_v} calculates it. Runs inside the caller's transaction.
	 *
	 * @param id employee id of an existing resource
	 * @return the situation code
	 */
	String situationOf(long id) {
		return situations.findById(id).map(ResourceSituation::getSituation).orElseThrow();
	}

}
