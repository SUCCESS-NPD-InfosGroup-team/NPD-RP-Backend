package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.npdinfosgroup.api.history.dto.AuditChange;
import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.UpdateResourceCommand;
import com.npdinfosgroup.api.planner.dto.request.UpdateResourceRequest;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.service.mapper.ResourceMapper;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * What happens when the write itself fails, which the API tests cannot time: another edit or
 * registration saved between the checks and the write.
 */
class ResourceUpdateServiceTest {

	private final ResourceRequestValidator validator = mock();

	private final EmployeeRepository employees = mock();

	private final AuditService audit = mock();

	private final ResourceUpdateService service = new ResourceUpdateService(validator, employees,
			mock(ResourceQueryService.class), audit, mock(ResourceMapper.class));

	private final UpdateResourceCommand command = new UpdateResourceCommand(7, new UpdateResourceRequest("1045",
			"Ana Torres", null, null, 1L, null, null, (short) 1, (short) 1, null, LocalDate.of(2026, 8, 3), false,
			"ACTIVO", null, null, 2), "mperez");

	/** The stored record has the version that was read, and the request is valid. */
	@BeforeEach
	void storedRecord() {
		Employee employee = new Employee();
		employee.setId(7L);
		employee.setVersion(2);
		when(employees.findDetailedById(7L)).thenReturn(Optional.of(employee));
		when(validator.validateUpdate(anyLong(), any())).thenReturn((short) 1);
	}

	/**
	 * A form read before someone else's edit gets the conflict with who saved, when and which fields;
	 * after a registration there are no changed fields to report.
	 */
	@Test
	void reportsWhoChangedTheRecordAndWhichFields() {
		Instant savedAt = Instant.parse("2026-10-30T15:31:00Z");
		when(audit.findLastChange("employee", 7L))
			.thenReturn(Optional.of(new AuditChange("jrios", savedAt, "UPDATE", List.of("assignment.project"))))
			.thenReturn(Optional.of(new AuditChange("jrios", savedAt, "CREATE", List.of("name"))));
		UpdateResourceCommand stale = new UpdateResourceCommand(7, new UpdateResourceRequest("1045", "Ana Torres",
				null, null, 1L, null, null, (short) 1, (short) 1, null, LocalDate.of(2026, 8, 3), false, "ACTIVO",
				null, null, 1), "mperez");

		assertThatThrownBy(() -> service.update(stale)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getProperties())
			.isEqualTo(Map.of("currentVersion", 2, "updatedBy", "jrios", "updatedFields", List.of("assignment.project")));
		assertThatThrownBy(() -> service.update(stale)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getProperties())
			.isEqualTo(Map.of("currentVersion", 2, "updatedBy", "jrios"));
	}

	/** An edit saved by someone else between the version check and the write is a conflict. */
	@Test
	void reportsConcurrentEditAsConflict() {
		doThrow(new ObjectOptimisticLockingFailureException(Employee.class, 7L)).when(employees).flush();

		assertThatThrownBy(() -> service.update(command)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
		verify(audit, never()).recordChanges(any(), anyLong(), any(), any(), any());
	}

	/** A concurrent registration of the same number is reported as a taken number. */
	@Test
	void reportsConcurrentDuplicateAsTakenNumber() {
		doThrow(new DataIntegrityViolationException("update",
				new SQLException("duplicate key value violates unique constraint \"employee_code_key\"")))
			.when(employees).flush();

		assertThatThrownBy(() -> service.update(command)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo(ErrorCode.EMPLOYEE_NUMBER_TAKEN);
	}

	/** Any other integrity failure is not hidden. */
	@Test
	void rethrowsOtherIntegrityFailures() {
		DataIntegrityViolationException failure = new DataIntegrityViolationException("update",
				new SQLException("duplicate key value violates unique constraint \"employee_email_uk\""));
		doThrow(failure).when(employees).flush();

		assertThatThrownBy(() -> service.update(command)).isSameAs(failure);
	}

}
