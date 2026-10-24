package com.npdinfosgroup.api.planner.service;

import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.response.ResourceResponse;
import com.npdinfosgroup.api.planner.persistence.repository.ResourceRepository;
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

	private final ResourceRepository resources;

	private final AuditService audit;

	private final ResourceMapper mapper;

	/**
	 * Creates the service.
	 *
	 * @param resources resource queries
	 * @param audit gives who changed the resource last
	 * @param mapper builds the response
	 */
	ResourceQueryService(ResourceRepository resources, AuditService audit, ResourceMapper mapper) {
		this.resources = resources;
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
		return resources.findResource(id)
			.map(row -> mapper.toResponse(row, audit.findLastActor(AUDIT_ENTITY, id).orElse(null)))
			.orElseThrow(() -> new NotFoundException(id));
	}

}
