package com.npdinfosgroup.api.planner.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.CreateProjectCommand;
import com.npdinfosgroup.api.planner.dto.request.CreateProjectRequest;
import com.npdinfosgroup.api.planner.dto.request.NewClientRequest;
import com.npdinfosgroup.api.planner.dto.response.ProjectResponse;
import com.npdinfosgroup.api.planner.persistence.entity.Client;
import com.npdinfosgroup.api.planner.persistence.entity.Project;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import com.npdinfosgroup.api.planner.service.validator.ProjectRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates projects (HU-07), from the Projects module or from "Crear proyecto" in the resource form,
 * for an existing client or together with a new one.
 * <p>
 * The new client, the project and their audit rows are saved in one transaction: a failure saves
 * nothing.
 */
@Service
public class ProjectRegistrationService {

	private static final Logger log = LoggerFactory.getLogger(ProjectRegistrationService.class);

	/** Unique index of {@code client.name}, created by V1. */
	private static final String CLIENT_NAME_INDEX = "client_name_uk";

	/** Unique index of the project name within its client, created by V1. */
	private static final String PROJECT_NAME_INDEX = "project_client_name_uk";

	private final ProjectRequestValidator validator;

	private final ClientRepository clients;

	private final ProjectRepository projects;

	private final ProjectQueryService queries;

	private final AuditService audit;

	private final MessageSource messages;

	/**
	 * Creates the service.
	 *
	 * @param validator checks the request
	 * @param clients saves the new client
	 * @param projects saves the project
	 * @param queries reads the saved project with its counts
	 * @param audit records the initial values
	 * @param messages texts of the audited values ("Sí", "No")
	 */
	ProjectRegistrationService(ProjectRequestValidator validator, ClientRepository clients,
			ProjectRepository projects, ProjectQueryService queries, AuditService audit, MessageSource messages) {
		this.validator = validator;
		this.clients = clients;
		this.projects = projects;
		this.queries = queries;
		this.audit = audit;
		this.messages = messages;
	}

	/**
	 * Creates a project, active and without team.
	 *
	 * @param command the project and who creates it
	 * @return the saved project
	 * @throws ValidationException with every invalid field
	 * @throws ApiException {@link ErrorCode#CLIENT_NAME_TAKEN} or {@link ErrorCode#PROJECT_NAME_TAKEN}
	 *         if a name is already taken
	 */
	@Transactional
	public ProjectResponse create(CreateProjectCommand command) {
		CreateProjectRequest request = command.project();
		Optional<Client> existingClient = validator.validateNew(request);
		boolean newClient = existingClient.isEmpty();
		Client client = existingClient.orElseGet(() -> toClient(request.newClient()));
		Project project = new Project();
		project.setClient(client);
		project.setName(request.name().strip());
		project.setContractEndDate(request.contractEndDate());
		project.setActive(true);
		try {
			if (newClient) {
				clients.save(client);
			}
			projects.saveAndFlush(project);
		}
		catch (DataIntegrityViolationException ex) {
			// Someone created the same name between the check and the insert
			throw nameTaken(ex, client, project);
		}
		if (newClient) {
			audit.recordCreation("client", client.getId(), clientValues(client), command.actor());
		}
		audit.recordCreation("project", project.getId(), projectValues(project), command.actor());
		log.info("Project {} created", project.getId());
		return queries.get(project.getId());
	}

	private static Client toClient(NewClientRequest request) {
		Client client = new Client();
		client.setName(request.name().strip());
		client.setInternal(Boolean.TRUE.equals(request.isInternal()));
		client.setActive(true);
		return client;
	}

	private Map<String, String> clientValues(Client client) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("name", client.getName());
		values.put("isInternal", messages.getMessage(client.isInternal() ? "audit.value.yes" : "audit.value.no", null,
				LocaleContextHolder.getLocale()));
		return values;
	}

	private static Map<String, String> projectValues(Project project) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("client", project.getClient().getName());
		values.put("name", project.getName());
		values.put("contractEndDate", Objects.toString(project.getContractEndDate(), null));
		return values;
	}

	private static RuntimeException nameTaken(DataIntegrityViolationException ex, Client client, Project project) {
		String message = ex.getMostSpecificCause().getMessage();
		// PostgreSQL quotes the index name; quoted, "client_name_uk" does not match "project_client_name_uk"
		if (message != null && message.contains('"' + CLIENT_NAME_INDEX + '"')) {
			return new ApiException(ErrorCode.CLIENT_NAME_TAKEN, List.of(client.getName()), Map.of());
		}
		if (message != null && message.contains('"' + PROJECT_NAME_INDEX + '"')) {
			return new ApiException(ErrorCode.PROJECT_NAME_TAKEN, List.of(client.getName(), project.getName()),
					Map.of());
		}
		return ex;
	}

}
