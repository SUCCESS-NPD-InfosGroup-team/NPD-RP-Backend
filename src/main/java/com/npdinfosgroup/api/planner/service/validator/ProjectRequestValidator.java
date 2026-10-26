package com.npdinfosgroup.api.planner.service.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.npdinfosgroup.api.planner.dto.request.CreateProjectRequest;
import com.npdinfosgroup.api.planner.persistence.entity.Client;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * Checks a new project before it is saved: first every invalid field at once (400), then the names
 * already taken (409), which the form shows apart with a way to pick the existing record.
 */
@Component
public class ProjectRequestValidator {

	/** Scope of the field messages of this form ({@code validation.project.<field>.<CODE>}). */
	static final String MESSAGE_SCOPE = "project";

	private static final String CLIENT_ID = "clientId";

	private final Validator validator;

	private final ClientRepository clients;

	private final ProjectRepository projects;

	/**
	 * Creates the validator.
	 *
	 * @param validator Jakarta Bean Validation
	 * @param clients client checks
	 * @param projects duplicate project checks
	 */
	ProjectRequestValidator(Validator validator, ClientRepository clients, ProjectRepository projects) {
		this.validator = validator;
		this.clients = clients;
		this.projects = projects;
	}

	/**
	 * Validates a new project.
	 *
	 * @param request the project to create
	 * @return the existing client of the project, or empty when the request creates a new client
	 * @throws ValidationException with every invalid field, when there is at least one
	 * @throws ApiException {@link ErrorCode#CLIENT_NAME_TAKEN} if the new client's name exists, or
	 *         {@link ErrorCode#PROJECT_NAME_TAKEN} if the client already has a project with that name
	 */
	public Optional<Client> validateNew(CreateProjectRequest request) {
		List<FieldProblem> problems = new ArrayList<>(ConstraintProblems.of(validator.validate(request), ""));
		Client client = null;
		if (request.clientId() != null && request.newClient() != null) {
			problems.add(FieldProblem.of(CLIENT_ID, FieldErrorCode.CONFLICTING_VALUES));
		}
		else if (request.clientId() == null && request.newClient() == null) {
			problems.add(FieldProblem.of(CLIENT_ID, FieldErrorCode.REQUIRED));
		}
		else if (request.newClient() != null) {
			problems.addAll(ConstraintProblems.of(validator.validate(request.newClient()), "newClient."));
		}
		else {
			client = clients.findById(request.clientId()).orElse(null);
			if (client == null) {
				problems.add(FieldProblem.of(CLIENT_ID, FieldErrorCode.UNKNOWN_ID));
			}
			else if (!client.isActive()) {
				problems.add(FieldProblem.of(CLIENT_ID, FieldErrorCode.INACTIVE_CLIENT));
			}
		}
		if (!problems.isEmpty()) {
			throw new ValidationException(MESSAGE_SCOPE, problems);
		}

		String name = request.name().strip();
		if (client == null) {
			String clientName = request.newClient().name().strip();
			clients.findFirstByNameIgnoreCase(clientName).ifPresent(existing -> {
				throw new ApiException(ErrorCode.CLIENT_NAME_TAKEN, List.of(existing.getName()),
						Map.of("existingClientId", existing.getId()));
			});
			return Optional.empty();
		}
		Client existingClient = client;
		projects.findFirstByClient_IdAndNameIgnoreCase(client.getId(), name).ifPresent(existing -> {
			throw new ApiException(ErrorCode.PROJECT_NAME_TAKEN, List.of(existingClient.getName(), existing.getName()),
					Map.of("existingProjectId", existing.getId()));
		});
		return Optional.of(client);
	}

}
