package com.npdinfosgroup.api.planner.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import com.npdinfosgroup.api.planner.dto.request.UpdateProjectRequest;
import com.npdinfosgroup.api.planner.persistence.entity.Client;
import com.npdinfosgroup.api.planner.persistence.entity.Project;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

/**
 * The rules of "Editar proyecto" (CA-06 of HU-14): every invalid field at once, the past date only when
 * it changes, and a name taken by another project of the same client.
 */
class ProjectRequestValidatorUpdateTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

	private final ProjectRepository projects = mock();

	private final ProjectRequestValidator validator = new ProjectRequestValidator(
			Validation.buildDefaultValidatorFactory().getValidator(), mock(ClientRepository.class), projects,
			Clock.fixed(TODAY.atTime(12, 0).atZone(BOGOTA).toInstant(), BOGOTA));

	/** Blank name, missing version and a new date in the past come together. */
	@Test
	void reportsEveryInvalidFieldAtOnce() {
		Project project = project(TODAY.plusDays(10));

		assertThatExceptionOfType(ValidationException.class)
			.isThrownBy(() -> validator.validateUpdate(project, new UpdateProjectRequest(" ", TODAY.minusDays(1), null)))
			.satisfies(ex -> assertThat(ex.getProblems()).extracting(FieldProblem::field)
				.containsExactlyInAnyOrder("name", "version", "contractEndDate"));
		assertThatExceptionOfType(ValidationException.class)
			.isThrownBy(() -> validator.validateUpdate(project, new UpdateProjectRequest("x".repeat(151), null, 1)))
			.satisfies(ex -> assertThat(ex.getProblems()).extracting(FieldProblem::code)
				.containsExactly(FieldErrorCode.TOO_LONG));
	}

	/** A contract end that already passed is accepted while it does not change; today is accepted. */
	@Test
	void checksThePastDateOnlyWhenItChanges() {
		Project expired = project(TODAY.minusDays(5));

		assertThatNoException().isThrownBy(
				() -> validator.validateUpdate(expired, new UpdateProjectRequest("Core", TODAY.minusDays(5), 1)));
		assertThatNoException()
			.isThrownBy(() -> validator.validateUpdate(expired, new UpdateProjectRequest("Core", TODAY, 1)));
		assertThatNoException()
			.isThrownBy(() -> validator.validateUpdate(expired, new UpdateProjectRequest("Core", null, 1)));
	}

	/** Another project of the client with the name is a 409 that points to it; none is fine. */
	@Test
	void checksTheNameWithinTheClient() {
		Project project = project(null);
		Project other = new Project();
		other.setId(9L);
		other.setName("Otro");
		when(projects.findFirstByClient_IdAndNameIgnoreCaseAndIdNot(3L, "otro", 7L)).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> validator.checkNameAvailable(project, "otro")).isInstanceOf(ApiException.class)
			.satisfies(ex -> {
				assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.PROJECT_NAME_TAKEN);
				assertThat(((ApiException) ex).getProperties()).containsEntry("existingProjectId", 9L);
			});
		assertThatNoException().isThrownBy(() -> validator.checkNameAvailable(project, "Nuevo"));
	}

	private static Project project(LocalDate contractEnd) {
		Client client = new Client();
		client.setId(3L);
		client.setName("Banco");
		Project project = new Project();
		project.setId(7L);
		project.setClient(client);
		project.setName("Core");
		project.setContractEndDate(contractEnd);
		return project;
	}

}
