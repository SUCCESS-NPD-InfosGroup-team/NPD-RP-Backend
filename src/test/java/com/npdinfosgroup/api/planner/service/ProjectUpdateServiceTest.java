package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.npdinfosgroup.api.history.dto.AuditChange;
import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.UpdateProjectCommand;
import com.npdinfosgroup.api.planner.dto.request.UpdateProjectRequest;
import com.npdinfosgroup.api.planner.persistence.entity.Client;
import com.npdinfosgroup.api.planner.persistence.entity.Project;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.planner.service.validator.ProjectRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * "Editar proyecto" without a database: the order of the checks, the 409 of a stale form, the races
 * caught at the write and the audit of what changed.
 */
class ProjectUpdateServiceTest {

	private static final String ACTOR = "ana.ops";

	private final ProjectRepository projects = mock();

	private final ProjectRequestValidator validator = mock();

	private final ProjectQueryService queries = mock();

	private final AuditService audit = mock();

	private final ProjectUpdateService service = new ProjectUpdateService(projects, validator, queries, audit);

	private Project project;

	/** An active project "Core" of client "Banco", at version 2, with a contract end. */
	@BeforeEach
	void createProject() {
		Client client = new Client();
		client.setName("Banco");
		project = new Project();
		project.setId(7L);
		project.setClient(client);
		project.setName("Core");
		project.setContractEndDate(LocalDate.of(2027, 1, 31));
		project.setActive(true);
		project.setVersion(2);
		when(projects.findById(7L)).thenReturn(Optional.of(project));
	}

	/** The new values are saved stripped, the audit gets both snapshots and the answer has the project. */
	@Test
	void savesAndAuditsTheChanges() {
		service.update(command(" Core 2 ", null, 2));

		assertThat(project.getName()).isEqualTo("Core 2");
		assertThat(project.getContractEndDate()).isNull();
		verify(validator).checkNameAvailable(project, "Core 2");
		verify(projects).saveAndFlush(project);
		Map<String, String> after = new HashMap<>();
		after.put("name", "Core 2");
		after.put("contractEndDate", null);
		verify(audit).recordChanges("project", 7L, Map.of("name", "Core", "contractEndDate", "2027-01-31"), after,
				ACTOR);
		verify(queries).get(7L);
	}

	/** An unknown project is a 404 and an inactive one a 409, before any other check. */
	@Test
	void rejectsUnknownAndInactiveProjects() {
		assertThatThrownBy(() -> service.update(new UpdateProjectCommand(8L, request("X", null, 0), ACTOR)))
			.isInstanceOf(NotFoundException.class);

		project.setActive(false);
		assertThatThrownBy(() -> service.update(command("X", null, 2))).isInstanceOf(ApiException.class)
			.extracting("code").isEqualTo(ErrorCode.PROJECT_INACTIVE);
		verify(validator, never()).validateUpdate(any(), any());
	}

	/** A form read before the last save is a 409 with the current version and who saved it. */
	@Test
	void rejectsAStaleVersion() {
		project.setUpdatedAt(Instant.parse("2026-11-25T20:00:00Z"));
		when(audit.findLastChange("project", 7L))
			.thenReturn(Optional.of(new AuditChange("luis.ops", Instant.now(), "UPDATE", List.of("name"))));

		assertThatThrownBy(() -> service.update(command("Core 2", null, 1))).isInstanceOf(ApiException.class)
			.satisfies(ex -> {
				ApiException api = (ApiException) ex;
				assertThat(api.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
				assertThat(api.getProperties()).containsEntry("currentVersion", 2)
					.containsEntry("updatedBy", "luis.ops")
					.containsEntry("updatedFields", List.of("name"))
					.containsKey("updatedAt");
			});
		verify(projects, never()).saveAndFlush(any());
	}

	/** A save by someone else or a name taken between the checks and the write are reported as 409. */
	@Test
	void reportsRacesCaughtAtTheWrite() {
		doThrow(new OptimisticLockingFailureException("stale")).when(projects).saveAndFlush(project);
		assertThatThrownBy(() -> service.update(command("Core 2", null, 2))).extracting("code")
			.isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);

		doThrow(new DataIntegrityViolationException("project_client_name_uk")).when(projects).saveAndFlush(project);
		project.setVersion(2);
		assertThatThrownBy(() -> service.update(command("Core 3", null, 2))).extracting("code")
			.isEqualTo(ErrorCode.PROJECT_NAME_TAKEN);
		verify(audit, never()).recordChanges(anyString(), anyLong(), anyMap(), anyMap(), eq(ACTOR));
	}

	private static UpdateProjectCommand command(String name, LocalDate contractEnd, Integer version) {
		return new UpdateProjectCommand(7L, request(name, contractEnd, version), ACTOR);
	}

	private static UpdateProjectRequest request(String name, LocalDate contractEnd, Integer version) {
		return new UpdateProjectRequest(name, contractEnd, version);
	}

}
