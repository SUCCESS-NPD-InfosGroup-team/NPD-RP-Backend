package com.npdinfosgroup.api.planner.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.response.AssignmentCandidateResponse;
import com.npdinfosgroup.api.planner.dto.response.AssignmentCandidatesResponse;
import com.npdinfosgroup.api.planner.dto.response.CodeLabel;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.persistence.entity.RosterEntry;
import com.npdinfosgroup.api.planner.persistence.query.EmployeeVersion;
import com.npdinfosgroup.api.planner.persistence.query.RosterEntrySpecifications;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.planner.persistence.repository.RosterEntryRepository;
import com.npdinfosgroup.api.shared.dto.PageInfo;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The people an Operations administrator can add to a project (HU-08, "Agregar colaboradores"): everyone
 * not on Baja and not already in it, those without a project first, the rest with the project they leave.
 */
@Service
public class AssignmentCandidateService {

	/** People on Baja are never assigned. */
	static final String TERMINATED_STATUS = "BAJA";

	private final RosterEntryRepository roster;

	private final EmployeeRepository employees;

	private final ProjectRepository projects;

	/**
	 * Creates the service.
	 *
	 * @param roster rows of the roster, with each person's current project
	 * @param employees current version of each person
	 * @param projects checks that the project exists
	 */
	AssignmentCandidateService(RosterEntryRepository roster, EmployeeRepository employees,
			ProjectRepository projects) {
		this.roster = roster;
		this.employees = employees;
		this.projects = projects;
	}

	/**
	 * A page of the people who can be added to a project.
	 *
	 * @param projectId the project
	 * @param text name or employee number to look for, ignoring case and accents; blank does not filter
	 * @param pageable page number and size; its sort is ignored, the order is always the same
	 * @return the page, with each person's version and the total without a project
	 * @throws NotFoundException if the project does not exist
	 */
	@Transactional(readOnly = true)
	public AssignmentCandidatesResponse findCandidates(long projectId, String text, Pageable pageable) {
		if (!projects.existsById(projectId)) {
			throw new NotFoundException(projectId);
		}
		List<Specification<RosterEntry>> filters = new ArrayList<>();
		filters.add(RosterEntrySpecifications.candidatesFor(projectId, TERMINATED_STATUS));
		if (text != null && !text.isBlank()) {
			filters.add(RosterEntrySpecifications.containing(ResourceSearchService.normalize(text)));
		}
		Specification<RosterEntry> candidates = Specification.allOf(filters);
		Page<RosterEntry> page = roster.findAll(candidates.and(RosterEntrySpecifications.withoutProjectFirst()),
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
		List<Long> ids = page.getContent().stream().map(RosterEntry::getId).toList();
		// One query for the versions of the whole page, never one per person
		Map<Long, Integer> versions = ids.isEmpty() ? Map.of()
				: employees.findVersions(ids).stream().collect(Collectors.toMap(EmployeeVersion::id, EmployeeVersion::version));
		List<AssignmentCandidateResponse> items = page.getContent()
			.stream()
			.map(row -> candidate(row, versions.get(row.getId())))
			.toList();
		return new AssignmentCandidatesResponse(items,
				new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()),
				roster.count(candidates.and(RosterEntrySpecifications.withoutProject())));
	}

	private static AssignmentCandidateResponse candidate(RosterEntry row, Integer version) {
		boolean hasProject = row.getProjectId() != null;
		return new AssignmentCandidateResponse(row.getId(), row.getEmployeeCode(), row.getFullName(), row.getLevel(),
				row.getClientRole(), new CodeLabel(row.getStatusCode(), row.getStatusName()),
				hasProject && row.getClientId() != null ? new IdName(row.getClientId(), row.getClientName()) : null,
				hasProject ? new IdName(row.getProjectId(), row.getProjectName()) : null, version);
	}

}
