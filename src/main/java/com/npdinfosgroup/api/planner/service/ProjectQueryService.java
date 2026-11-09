package com.npdinfosgroup.api.planner.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.request.ContractEndFilter;
import com.npdinfosgroup.api.planner.dto.request.ProjectSearchCriteria;
import com.npdinfosgroup.api.planner.dto.response.ClientResponse;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.ItemsResponse;
import com.npdinfosgroup.api.planner.dto.response.PageInfo;
import com.npdinfosgroup.api.planner.dto.response.PageResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectKpisResponse;
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
import com.npdinfosgroup.api.shared.dto.PageInfo;
import com.npdinfosgroup.api.shared.dto.PageResponse;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the projects: the list of the Projects module and its KPIs, one project, and the projects of
 * a client for the selectors of the resource form.
 * <p>
 * The list and the KPIs share the same filters, so the figures always describe what the list shows.
 * Dates such as «Próximos 30 días» are calculated with the application clock (America/Bogota), never
 * with the browser's.
 */
@Service
public class ProjectQueryService {

	/** The list goes grouped by client, so it is always ordered by client and then by project. */
	private static final Sort LIST_ORDER = Sort.by("clientName", "name", "id");

	/** Days to the client contract end at which a project is "ending soon" and "D-N" goes red. */
	static final int CONTRACT_END_SOON_DAYS = 30;

	/** Status of the employees counted as «Colaboradores sin proyecto» (Licencia and Baja are not). */
	static final String ACTIVE_STATUS = "ACTIVO";

	/** Longest «Buscar» text that is used; the longest project or client name is 150 characters. */
	static final int MAX_SEARCH_LENGTH = 150;

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
		// One "today" for the filters and for daysToContractEnd, even if the call crosses midnight
		LocalDate today = LocalDate.now(clock);
		Page<ProjectSummary> page = projectSummaries.findAll(filters(criteria, today),
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER));
		Map<Long, List<IdName>> leads = leadsOf(page.getContent());
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
	 * The KPIs above the projects list, calculated over every project that matches the filters (not
	 * only the page on screen).
	 * <p>
	 * The projects module has a few dozen rows, so the matching projects are read once and counted in
	 * memory; if it ever grows to thousands, this becomes an aggregate query.
	 *
	 * @param criteria the same filters as the list
	 * @return the KPIs
	 */
	@Transactional(readOnly = true)
	public ProjectKpisResponse kpis(ProjectSearchCriteria criteria) {
		LocalDate today = LocalDate.now(clock);
		LocalDate soonLimit = today.plusDays(CONTRACT_END_SOON_DAYS);
		List<ProjectSummary> projects = projectSummaries.findAll(filters(criteria, today));
		long clientCount = projects.stream().map(ProjectSummary::getClientId).distinct().count();
		long withoutTeam = projects.stream().filter(p -> countOf(p.getTeamSize()) == 0).count();
		long endingSoon = projects.stream()
			.map(ProjectSummary::getContractEndDate)
			.filter(end -> end != null && !end.isBefore(today) && !end.isAfter(soonLimit))
			.count();
		long pending = projects.stream().mapToLong(p -> countOf(p.getPendingConfirmation())).sum();
		return new ProjectKpisResponse(projects.size(), clientCount, withoutTeam, endingSoon, pending,
				employees.countWithoutProject(ACTIVE_STATUS));
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

	/**
	 * The filters of the list and the KPIs, combined with AND.
	 *
	 * @param criteria what the user chose
	 * @param today today in the application time zone, for «Próximos 30 días»
	 * @return the combined filter
	 */
	private static Specification<ProjectSummary> filters(ProjectSearchCriteria criteria, LocalDate today) {
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
		if (criteria.contractEnd() == ContractEndFilter.NEXT_30_DAYS) {
			filters.add(ProjectSummarySpecifications.contractEndingBetween(today,
					today.plusDays(CONTRACT_END_SOON_DAYS)));
		}
		else if (criteria.contractEnd() == ContractEndFilter.NONE) {
			filters.add(ProjectSummarySpecifications.withoutContractEnd());
		}
		String search = normalizedSearch(criteria.search());
		if (search != null) {
			filters.add(ProjectSummarySpecifications.nameContains(search));
		}
		return Specification.allOf(filters);
	}

	/**
	 * The «Buscar» text that is actually used: stripped, {@code null} when blank, and cut at
	 * {@code MAX_SEARCH_LENGTH} (150) so a pasted paragraph cannot make a huge pattern.
	 *
	 * @param search the text the user typed, may be {@code null}
	 * @return the text to search, or {@code null} to not filter
	 */
	static String normalizedSearch(String search) {
		if (search == null || search.isBlank()) {
			return null;
		}
		String text = search.strip();
		return text.length() > MAX_SEARCH_LENGTH ? text.substring(0, MAX_SEARCH_LENGTH) : text;
	}

	/**
	 * A count of the view, which is {@code null} only if the row has no aggregates.
	 *
	 * @param value the count
	 * @return the count, 0 when {@code null}
	 */
	private static long countOf(Long value) {
		return value == null ? 0 : value;
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
