package com.npdinfosgroup.api.planner.service;

import com.npdinfosgroup.api.planner.dto.response.ItemsResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectOptionResponse;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectSummaryRepository;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the projects of a client for the selectors of the resource form.
 */
@Service
public class ProjectQueryService {

	private final ClientRepository clients;

	private final ProjectSummaryRepository projectSummaries;

	/**
	 * Creates the service.
	 *
	 * @param clients checks that the client exists
	 * @param projectSummaries projects with their team size
	 */
	ProjectQueryService(ClientRepository clients, ProjectSummaryRepository projectSummaries) {
		this.clients = clients;
		this.projectSummaries = projectSummaries;
	}

	/**
	 * The active projects of a client, with their team size and contract end, for the "Proyecto
	 * asignado" selector. Inactive projects are left out because a resource can only be assigned to an
	 * active one.
	 *
	 * @param clientId client id
	 * @return its active projects; an empty list if it has none
	 * @throws NotFoundException if the client does not exist
	 */
	@Transactional(readOnly = true)
	public ItemsResponse<ProjectOptionResponse> findActiveByClient(long clientId) {
		if (!clients.existsById(clientId)) {
			throw new NotFoundException(clientId);
		}
		return new ItemsResponse<>(projectSummaries.findByClientIdAndActiveTrueOrderByNameAsc(clientId)
			.stream()
			.map(p -> new ProjectOptionResponse(p.getId(), p.getName(), p.getTeamSize(), p.getContractEndDate()))
			.toList());
	}

}
