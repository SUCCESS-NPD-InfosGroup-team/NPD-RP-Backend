package com.npdinfosgroup.api.planner.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.request.ProjectSearchCriteria;
import com.npdinfosgroup.api.planner.dto.response.ClientResponse;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.ItemsResponse;
import com.npdinfosgroup.api.planner.dto.response.PageInfo;
import com.npdinfosgroup.api.planner.dto.response.PageResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectListItemResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectOptionResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectResponse;
import com.npdinfosgroup.api.planner.persistence.entity.ProjectSummary;
import com.npdinfosgroup.api.planner.persistence.query.ProjectLead;
import com.npdinfosgroup.api.planner.persistence.query.ProjectSummarySpecifications;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectSummaryRepository;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the projects: the list of the Projects module, one project, and the projects of a client for
 * the selectors of the resource form.
 */
@Service
public class ProjectQueryService {

	/** The list goes grouped by client, so it is always ordered by client and then by project. */
	private static final Sort LIST_ORDER = Sort.by("clientName", "name", "id");

	private final ClientRepository clients;

	private final ProjectSummaryRepository projectSummaries;

	private final EmployeeRepository employees;

	private final Clock clock;

	/**
	 * Creates the service.
	 *
	 * @param clients checks that the client exists
	 * @param projectSummaries projects with their team counts
	 * @param employees gives the NPD leads of each project
	 * @param clock gives today for the days to the contract end
	 */
	ProjectQueryService(ClientRepository clients, ProjectSummaryRepository projectSummaries,
			EmployeeRepository employees, Clock clock) {
		this.clients = clients;
		this.projectSummaries = projectSummaries;
		this.employees = employees;
		this.clock = clock;
	}

	/**
	 * A page of the projects list, ordered by client and then by project, with the team counts and the
	 * NPD leads of each one.
	 *
	 * @param criteria filters; the inactive projects are left out unless asked for
	 * @param pageable page number and size; its sort is ignored, the order is always the same
	 * @return the page
	 */
	@Transactional(readOnly = true)
	public PageResponse<ProjectListItemResponse> list(ProjectSearchCriteria criteria, Pageable pageable) {
		Page<ProjectSummary> page = projectSummaries.findAll(filters(criteria),
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER));
		Map<Long, List<IdName>> leads = leadsOf(page.getContent());
		LocalDate today = LocalDate.now(clock);
		List<ProjectListItemResponse> items = page.getContent()
			.stream()
			.map(project -> new ProjectListItemResponse(project.getId(), project.getName(), clientOf(project),
					project.isActive(), project.getStartDate(), project.getContractEndDate(),
					project.getContractEndDate() == null ? null
							: ChronoUnit.DAYS.between(today, project.getContractEndDate()),
					project.getTeamSize(), project.getPendingConfirmation(), project.getIndefiniteCount(),
					project.getNextAssignmentEnd(), leads.getOrDefault(project.getId(), List.of())))
			.toList();
		return new PageResponse<>(items,
				new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
	}

	/**
	 * A project with its client and team size.
	 *
	 * @param id project id
	 * @return the project
	 * @throws NotFoundException if the project does not exist
	 */
	@Transactional(readOnly = true)
	public ProjectResponse get(long id) {
		ProjectSummary project = projectSummaries.findById(id).orElseThrow(() -> new NotFoundException(id));
		return new ProjectResponse(project.getId(), project.getName(), clientOf(project), project.isActive(),
				project.getStartDate(), project.getContractEndDate(), project.getTeamSize());
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

	private static Specification<ProjectSummary> filters(ProjectSearchCriteria criteria) {
		List<Specification<ProjectSummary>> filters = new ArrayList<>();
		if (!criteria.includeInactive()) {
			filters.add(ProjectSummarySpecifications.active());
		}
		if (criteria.clientId() != null) {
			filters.add(ProjectSummarySpecifications.ofClient(criteria.clientId()));
		}
		if (criteria.leaderId() != null) {
			filters.add(ProjectSummarySpecifications.withCollaboratorOf(criteria.leaderId(),
					ResourceRequestValidator.TERMINATED));
		}
		if (criteria.contractEndBefore() != null) {
			filters.add(ProjectSummarySpecifications.contractEndingBy(criteria.contractEndBefore()));
		}
		return Specification.allOf(filters);
	}

	private Map<Long, List<IdName>> leadsOf(List<ProjectSummary> projects) {
		if (projects.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = projects.stream().map(ProjectSummary::getId).toList();
		return employees.findProjectLeads(ids, ResourceRequestValidator.TERMINATED)
			.stream()
			.collect(Collectors.groupingBy(ProjectLead::projectId,
					Collectors.mapping(lead -> new IdName(lead.leaderId(), lead.leaderName()), Collectors.toList())));
	}

	private static ClientResponse clientOf(ProjectSummary project) {
		return new ClientResponse(project.getClientId(), project.getClientName(), project.isClientInternal());
	}

}
