package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.npdinfosgroup.api.access.service.UserQueryService;
import com.npdinfosgroup.api.history.dto.response.LatestChange;
import com.npdinfosgroup.api.history.service.AuditQueryService;
import com.npdinfosgroup.api.notifications.service.NoticeScheduleService;
import com.npdinfosgroup.api.planner.dto.response.OperationsStatusResponse;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The data of the status bar (HU-11) without a database: how each piece is gathered, and the empty
 * values when there is no change yet or no send scheduled.
 */
class OperationsStatusServiceTest {

	private static final Instant CHANGED_AT = Instant.parse("2026-10-07T14:12:00Z");

	private static final Instant NEXT_SEND = Instant.parse("2026-11-28T12:00:00Z");

	private final EmployeeRepository employees = mock();

	private final ProjectRepository projects = mock();

	private final ClientRepository clients = mock();

	private final AuditQueryService audit = mock();

	private final UserQueryService users = mock();

	private final NoticeScheduleService notices = mock();

	private final OperationsStatusService service = new OperationsStatusService(employees, projects, clients, audit,
			users, notices);

	/** The three counts, with «recursos activos» on the Activo status of the resource search. */
	@BeforeEach
	void counts() {
		when(employees.countByStatus_Code(ProjectQueryService.ACTIVE_STATUS)).thenReturn(142L);
		when(projects.countByActiveTrue()).thenReturn(31L);
		when(clients.countByActiveTrue()).thenReturn(12L);
	}

	/** Every field filled: the author comes back by name. */
	@Test
	void gathersEveryPiece() {
		when(audit.findLatestChange()).thenReturn(Optional.of(new LatestChange("maria.perez@infosgroup.com", CHANGED_AT)));
		when(users.displayNameOf("maria.perez@infosgroup.com")).thenReturn("María Pérez");
		when(notices.nextSend()).thenReturn(Optional.of(NEXT_SEND));

		assertThat(service.status())
			.isEqualTo(new OperationsStatusResponse(CHANGED_AT, "María Pérez", NEXT_SEND, 142, 31, 12));
	}

	/**
	 * With only imported data and no send scheduled, the dates and the author are empty and the counts
	 * are still there; no name is looked up.
	 */
	@Test
	void leavesEmptyWhatDoesNotExistYet() {
		when(audit.findLatestChange()).thenReturn(Optional.empty());
		when(notices.nextSend()).thenReturn(Optional.empty());

		assertThat(service.status()).isEqualTo(new OperationsStatusResponse(null, null, null, 142, 31, 12));
		verifyNoInteractions(users);
	}

}
