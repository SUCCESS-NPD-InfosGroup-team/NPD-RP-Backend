package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.npdinfosgroup.api.planner.dto.request.ProjectSearchCriteria;
import com.npdinfosgroup.api.planner.dto.response.ProjectKpisResponse;
import com.npdinfosgroup.api.planner.persistence.entity.ProjectSummary;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

/**
 * The KPIs of the Projects module and the normalization of the «Buscar» text, without a database:
 * the counting rules at the edges of the 30-day window and with empty aggregates.
 */
class ProjectQueryServiceTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	/** 23:30 in Bogota is already the next day in UTC: the window must use the Bogota date. */
	private static final Clock CLOCK = Clock.fixed(
			LocalDate.of(2026, 10, 31).atTime(23, 30).atZone(BOGOTA).toInstant(), BOGOTA);

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

	private final ProjectSummaryRepository summaries = mock();

	private final EmployeeRepository employees = mock();

	private final ProjectQueryService service = new ProjectQueryService(mock(ClientRepository.class), summaries,
			employees, CLOCK);

	private final ProjectSearchCriteria noFilters = new ProjectSearchCriteria(null, null, null, null, null, false);

	/**
	 * Counts over every matching project: distinct clients, «Sin equipo», contracts ending from today to
	 * today + 30 (both included, expired ones out) and the pending confirmations.
	 */
	@Test
	void countsKpisOverEveryMatchingProject() {
		List<ProjectSummary> projects = List.of(
				project(1L, 0L, 0L, TODAY),                // ends today: soon
				project(1L, 3L, 2L, TODAY.plusDays(30)),   // last day of the window: soon
				project(2L, 1L, 1L, TODAY.plusDays(31)),   // one day too late
				project(2L, 0L, 0L, TODAY.minusDays(1)),   // already ended: not soon
				project(3L, 5L, 0L, null));                // no contract end
		when(summaries.findAll(any(Specification.class))).thenReturn(projects);
		when(employees.countWithoutProject(ProjectQueryService.ACTIVE_STATUS)).thenReturn(7L);

		ProjectKpisResponse kpis = service.kpis(noFilters);

		assertThat(kpis).isEqualTo(new ProjectKpisResponse(5, 3, 2, 2, 3, 7));
	}

	/** A row without aggregates counts as an empty team, not as an error. */
	@Test
	void treatsMissingCountsAsZero() {
		List<ProjectSummary> projects = List.of(project(1L, null, null, null));
		when(summaries.findAll(any(Specification.class))).thenReturn(projects);

		ProjectKpisResponse kpis = service.kpis(noFilters);

		assertThat(kpis.withoutTeam()).isEqualTo(1);
		assertThat(kpis.pendingConfirmation()).isZero();
	}

	/** No matching project gives zeros, not a failure. */
	@Test
	void givesZerosWithoutProjects() {
		when(summaries.findAll(any(Specification.class))).thenReturn(List.of());

		assertThat(service.kpis(noFilters)).isEqualTo(new ProjectKpisResponse(0, 0, 0, 0, 0, 0));
	}

	/** Blank text does not filter; the rest is stripped and cut at 150 characters. */
	@Test
	void normalizesTheSearchText() {
		assertThat(ProjectQueryService.normalizedSearch(null)).isNull();
		assertThat(ProjectQueryService.normalizedSearch("   ")).isNull();
		assertThat(ProjectQueryService.normalizedSearch("  Banesco ")).isEqualTo("Banesco");
		assertThat(ProjectQueryService.normalizedSearch("x".repeat(200)))
			.hasSize(ProjectQueryService.MAX_SEARCH_LENGTH);
	}

	private static ProjectSummary project(Long clientId, Long teamSize, Long pending, LocalDate contractEnd) {
		ProjectSummary project = mock();
		when(project.getClientId()).thenReturn(clientId);
		when(project.getTeamSize()).thenReturn(teamSize);
		when(project.getPendingConfirmation()).thenReturn(pending);
		when(project.getContractEndDate()).thenReturn(contractEnd);
		return project;
	}

}
