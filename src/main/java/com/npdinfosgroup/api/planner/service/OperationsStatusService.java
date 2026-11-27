package com.npdinfosgroup.api.planner.service;

import java.util.Optional;

import com.npdinfosgroup.api.access.service.UserQueryService;
import com.npdinfosgroup.api.history.dto.response.LatestChange;
import com.npdinfosgroup.api.history.service.AuditQueryService;
import com.npdinfosgroup.api.notifications.service.NoticeScheduleService;
import com.npdinfosgroup.api.planner.dto.response.OperationsStatusResponse;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the data of the status bar shown at the bottom of every screen (HU-11): how current the data
 * is, the main counts and the next notice send. It gathers each piece from the module that owns it.
 */
@Service
public class OperationsStatusService {

	private final EmployeeRepository employees;

	private final ProjectRepository projects;

	private final ClientRepository clients;

	private final AuditQueryService audit;

	private final UserQueryService users;

	private final NoticeScheduleService notices;

	/**
	 * Creates the service.
	 *
	 * @param employees counts the active resources
	 * @param projects counts the active projects
	 * @param clients counts the active clients
	 * @param audit gives the latest change of the data
	 * @param users turns the author of that change into a name
	 * @param notices gives the next send of the e-mail notices
	 */
	OperationsStatusService(EmployeeRepository employees, ProjectRepository projects, ClientRepository clients,
			AuditQueryService audit, UserQueryService users, NoticeScheduleService notices) {
		this.employees = employees;
		this.projects = projects;
		this.clients = clients;
		this.audit = audit;
		this.users = users;
		this.notices = notices;
	}

	/**
	 * The status of the data. The counts are global: they never depend on the filters of a screen.
	 * «Recursos activos» uses the same status as the default filter of the resource search, so the bar
	 * and the list agree.
	 *
	 * @return the status; the last update and the next send are {@code null} when there are none
	 */
	@Transactional(readOnly = true)
	public OperationsStatusResponse status() {
		Optional<LatestChange> latest = audit.findLatestChange();
		return new OperationsStatusResponse(
				latest.map(LatestChange::occurredAt).orElse(null),
				latest.map(change -> users.displayNameOf(change.actor())).orElse(null),
				notices.nextSend().orElse(null),
				employees.countByStatus_Code(ProjectQueryService.ACTIVE_STATUS),
				projects.countByActiveTrue(),
				clients.countByActiveTrue());
	}

}
