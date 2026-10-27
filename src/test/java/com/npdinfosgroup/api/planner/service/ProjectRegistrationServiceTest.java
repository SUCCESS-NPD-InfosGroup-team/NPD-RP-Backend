package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;

import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.CreateProjectCommand;
import com.npdinfosgroup.api.planner.dto.request.CreateProjectRequest;
import com.npdinfosgroup.api.planner.dto.request.NewClientRequest;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.planner.service.validator.ProjectRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * What happens when the insert itself fails, which the API tests cannot time: another creation with
 * the same name between the check and the insert.
 */
class ProjectRegistrationServiceTest {

	private final ProjectRequestValidator validator = mock();

	private final ProjectRepository projects = mock();

	private final ProjectRegistrationService service = new ProjectRegistrationService(validator,
			mock(ClientRepository.class), projects, mock(ProjectQueryService.class), mock(AuditService.class),
			mock(MessageSource.class));

	private final CreateProjectCommand command = new CreateProjectCommand(
			new CreateProjectRequest(null, new NewClientRequest("TIGO", null), "TIGO - Soporte QA", null), "mperez");

	/** The request itself is valid and creates a new client; only the insert fails. */
	@BeforeEach
	void acceptRequest() {
		when(validator.validateNew(any())).thenReturn(Optional.empty());
	}

	/** A concurrent creation of the same client is reported as a taken client name. */
	@Test
	void reportsConcurrentClientAsTakenName() {
		failInsertWith("duplicate key value violates unique constraint \"client_name_uk\"");

		assertThatThrownBy(() -> service.create(command)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo(ErrorCode.CLIENT_NAME_TAKEN);
	}

	/** A concurrent creation of the same project is reported as a taken project name. */
	@Test
	void reportsConcurrentProjectAsTakenName() {
		failInsertWith("duplicate key value violates unique constraint \"project_client_name_uk\"");

		assertThatThrownBy(() -> service.create(command)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo(ErrorCode.PROJECT_NAME_TAKEN);
	}

	/** Any other integrity failure is not hidden. */
	@Test
	void rethrowsOtherIntegrityFailures() {
		DataIntegrityViolationException failure = failInsertWith("null value in column \"name\"");

		assertThatThrownBy(() -> service.create(command)).isSameAs(failure);
	}

	private DataIntegrityViolationException failInsertWith(String message) {
		DataIntegrityViolationException failure = new DataIntegrityViolationException("insert",
				new SQLException(message));
		when(projects.saveAndFlush(any())).thenThrow(failure);
		return failure;
	}

}
