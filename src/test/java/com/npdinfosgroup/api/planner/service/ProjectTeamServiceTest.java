package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.npdinfosgroup.api.planner.dto.response.CodeLabel;
import com.npdinfosgroup.api.planner.dto.response.ProjectMemberResponse;
import com.npdinfosgroup.api.planner.dto.response.ProjectMembersResponse;
import com.npdinfosgroup.api.planner.persistence.entity.RosterEntry;
import com.npdinfosgroup.api.planner.persistence.query.EmployeeVersion;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.planner.persistence.repository.RosterEntryRepository;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.data.jpa.domain.Specification;

/**
 * The team of a project without a database: the order of the table, the versions read in one query and
 * the safety limit of rows.
 */
class ProjectTeamServiceTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

	private static final Clock CLOCK = Clock.fixed(TODAY.atTime(12, 0).atZone(BOGOTA).toInstant(), BOGOTA);

	private final ProjectRepository projects = mock();

	private final RosterEntryRepository roster = mock();

	private final EmployeeRepository employees = mock();

	private final ProjectTeamService service = new ProjectTeamService(projects, roster, employees,
			new StaticMessageSource(), CLOCK);

	/** Pending confirmation first, then by end date, the indefinite last, and ties by name. */
	@Test
	void ordersTheTeamForTheTable() {
		// The rows are mocks with their own stubs: create them before stubbing findAll
		List<RosterEntry> rows = List.of(
				row(1L, "Zoe", "ASIGNADO", null),
				row(2L, "Carlos", "ASIGNADO", TODAY.plusDays(40)),
				row(3L, "Ana", "ASIGNADO", TODAY.plusDays(40)),
				row(4L, "Pedro", "PENDIENTE_CONFIRMACION", TODAY.minusDays(3)),
				row(5L, "Luz", "ASIGNADO", TODAY.plusDays(5)));
		when(projects.existsById(7L)).thenReturn(true);
		when(roster.findAll(any(Specification.class))).thenReturn(rows);
		when(employees.findVersions(anyCollection())).thenReturn(List.of(new EmployeeVersion(4L, 6)));

		ProjectMembersResponse team = service.members(7L);

		assertThat(team.items()).extracting(ProjectMemberResponse::name)
			.containsExactly("Pedro", "Luz", "Ana", "Carlos", "Zoe");
		assertThat(team.total()).isEqualTo(5);
		ProjectMemberResponse pending = team.items().get(0);
		assertThat(pending.daysToAssignmentEnd()).isEqualTo(-3L);
		assertThat(pending.version()).isEqualTo(6);
		// Without a message for the code, the label falls back to the code
		assertThat(pending.situation()).isEqualTo(new CodeLabel("PENDIENTE_CONFIRMACION", "PENDIENTE_CONFIRMACION"));
		ProjectMemberResponse indefinite = team.items().get(4);
		assertThat(indefinite.indefinite()).isTrue();
		assertThat(indefinite.daysToAssignmentEnd()).isNull();
		assertThat(indefinite.leader()).isNull();
	}

	/** An empty team reads no versions; an unknown project is a 404. */
	@Test
	void handlesEmptyTeamsAndUnknownProjects() {
		when(projects.existsById(7L)).thenReturn(true);
		when(roster.findAll(any(Specification.class))).thenReturn(List.of());

		assertThat(service.members(7L).items()).isEmpty();
		verify(employees, never()).findVersions(anyCollection());
		assertThatThrownBy(() -> service.members(8L)).isInstanceOf(NotFoundException.class);
	}

	/** The table is cut at the safety limit, but the total is the real team size. */
	@Test
	void capsTheRowsButNotTheTotal() {
		List<RosterEntry> rows = new ArrayList<>();
		for (long id = 1; id <= ProjectTeamService.MAX_MEMBERS + 5; id++) {
			rows.add(row(id, "Persona " + id, "ASIGNADO", TODAY.plusDays(id)));
		}
		when(projects.existsById(7L)).thenReturn(true);
		when(roster.findAll(any(Specification.class))).thenReturn(rows);

		ProjectMembersResponse team = service.members(7L);

		assertThat(team.items()).hasSize(ProjectTeamService.MAX_MEMBERS);
		assertThat(team.total()).isEqualTo(ProjectTeamService.MAX_MEMBERS + 5);
	}

	private static RosterEntry row(long id, String name, String situation, LocalDate end) {
		RosterEntry row = mock();
		when(row.getId()).thenReturn(id);
		when(row.getFullName()).thenReturn(name);
		when(row.getSituation()).thenReturn(situation);
		when(row.getProjectEndDate()).thenReturn(end);
		when(row.getStatusCode()).thenReturn("ACTIVO");
		// Mockito answers 0 for a Long getter; a row without NPD lead has null, as in the view
		when(row.getLeaderId()).thenReturn(null);
		return row;
	}

}
