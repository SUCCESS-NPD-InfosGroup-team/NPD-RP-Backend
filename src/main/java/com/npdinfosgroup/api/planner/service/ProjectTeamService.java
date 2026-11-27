package com.npdinfosgroup.api.planner.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.response.CodeLabel;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.ProjectMemberResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectMembersResponse;
import com.npdinfosgroup.api.planner.persistence.entity.RosterEntry;
import com.npdinfosgroup.api.planner.persistence.query.EmployeeVersion;
import com.npdinfosgroup.api.planner.persistence.query.RosterEntrySpecifications;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.planner.persistence.repository.RosterEntryRepository;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the team of a project for the project detail (HU-13): the table and the timeline.
 * <p>
 * The team is the people whose current project is this one, Baja excluded, the same rule as
 * {@code teamSize} in {@code project_summary_v}, so the header count and the table always match. The rows
 * come from {@code resource_v}, which already has the situation and the start and end of each bar.
 */
@Service
public class ProjectTeamService {

	/** Safety limit of rows; a project team is a few dozen people at most. */
	static final int MAX_MEMBERS = 200;

	/** Situation of a person whose end date has passed and nobody confirmed or extended. */
	static final String PENDING_CONFIRMATION = "PENDIENTE_CONFIRMACION";

	/**
	 * Pending confirmation first (they need action), then by end date with the indefinite last, then by
	 * name.
	 */
	static final Comparator<ProjectMemberResponse> TEAM_ORDER = Comparator
		.comparing((ProjectMemberResponse member) -> !PENDING_CONFIRMATION.equals(member.situation().code()))
		.thenComparing(ProjectMemberResponse::assignmentEnd, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
		.thenComparing(ProjectMemberResponse::name, String.CASE_INSENSITIVE_ORDER)
		.thenComparingLong(ProjectMemberResponse::id);

	private final ProjectRepository projects;

	private final RosterEntryRepository roster;

	private final EmployeeRepository employees;

	private final MessageSource messages;

	private final Clock clock;

	/**
	 * Creates the service.
	 *
	 * @param projects checks that the project exists
	 * @param roster the rows of {@code resource_v}
	 * @param employees gives the version of each member
	 * @param messages labels of the situations
	 * @param clock gives today for the days to each end date
	 */
	ProjectTeamService(ProjectRepository projects, RosterEntryRepository roster, EmployeeRepository employees,
			MessageSource messages, Clock clock) {
		this.projects = projects;
		this.roster = roster;
		this.employees = employees;
		this.messages = messages;
		this.clock = clock;
	}

	/**
	 * The team of a project, active or not, in the order of the project detail.
	 *
	 * @param projectId project id
	 * @return the members (at most {@value #MAX_MEMBERS}) and how many there are
	 * @throws NotFoundException if the project does not exist
	 */
	@Transactional(readOnly = true)
	public ProjectMembersResponse members(long projectId) {
		if (!projects.existsById(projectId)) {
			throw new NotFoundException(projectId);
		}
		LocalDate today = LocalDate.now(clock);
		List<RosterEntry> rows = roster.findAll(Specification.allOf(
				RosterEntrySpecifications.equalTo("projectId", projectId),
				Specification.not(RosterEntrySpecifications.equalTo("statusCode", ResourceRequestValidator.TERMINATED))));
		Map<Long, Integer> versions = versionsOf(rows);
		List<ProjectMemberResponse> items = rows.stream()
			.map(row -> member(row, versions.get(row.getId()), today))
			.sorted(TEAM_ORDER)
			.limit(MAX_MEMBERS)
			.toList();
		return new ProjectMembersResponse(items, rows.size());
	}

	/**
	 * The version of every member in one query (never one per person).
	 *
	 * @param rows the members
	 * @return version by employee id; empty without members
	 */
	private Map<Long, Integer> versionsOf(List<RosterEntry> rows) {
		if (rows.isEmpty()) {
			return Map.of();
		}
		return employees.findVersions(rows.stream().map(RosterEntry::getId).toList())
			.stream()
			.collect(Collectors.toMap(EmployeeVersion::id, EmployeeVersion::version));
	}

	/**
	 * One row of the team table.
	 *
	 * @param row the member in {@code resource_v}
	 * @param version the member's version
	 * @param today today in the application time zone
	 * @return the row
	 */
	private ProjectMemberResponse member(RosterEntry row, Integer version, LocalDate today) {
		LocalDate end = row.getProjectEndDate();
		return new ProjectMemberResponse(row.getId(), row.getEmployeeCode(), row.getFullName(), row.getLevel(),
				row.getClientRole(), row.getLeaderId() == null ? null : new IdName(row.getLeaderId(), row.getLeaderName()),
				new CodeLabel(row.getStatusCode(), row.getStatusName()),
				new CodeLabel(row.getSituation(), messages.getMessage("resource.situation." + row.getSituation(), null,
						row.getSituation(), LocaleContextHolder.getLocale())),
				row.getAssignmentStartDate(), end, end == null, end == null ? null : ChronoUnit.DAYS.between(today, end),
				version, row.getEquipmentProvider(), row.getClientContactEmail());
	}

}
