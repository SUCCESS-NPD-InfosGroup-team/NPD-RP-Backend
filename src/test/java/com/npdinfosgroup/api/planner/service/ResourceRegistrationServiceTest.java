package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDate;

import com.npdinfosgroup.api.history.service.AuditService;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceCommand;
import com.npdinfosgroup.api.planner.dto.request.RegisterResourceRequest;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.service.mapper.ResourceMapper;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * What happens when the insert itself fails, which the API tests cannot time: another registration of
 * the same employee number between the check and the insert.
 */
class ResourceRegistrationServiceTest {

	private final ResourceRequestValidator validator = mock();

	private final EmployeeRepository employees = mock();

	private final ResourceRegistrationService service = new ResourceRegistrationService(validator, employees,
			mock(ResourceQueryService.class), mock(AuditService.class), mock(ResourceMapper.class));

	private final RegisterResourceCommand command = new RegisterResourceCommand(new RegisterResourceRequest("1045",
			"Ana Torres", null, null, 1L, null, null, (short) 1, (short) 1, null, LocalDate.of(2026, 8, 3), "ACTIVO",
			null, null), "mperez");

	/** The request itself is valid; only the insert fails. */
	@BeforeEach
	void acceptRequest() {
		when(validator.validateNew(any())).thenReturn((short) 1);
	}

	/** A concurrent registration of the same number is reported as a taken number. */
	@Test
	void reportsConcurrentDuplicateAsTakenNumber() {
		when(employees.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("insert",
				new SQLException("duplicate key value violates unique constraint \"employee_code_key\"")));

		assertThatThrownBy(() -> service.register(command)).isInstanceOf(ApiException.class)
			.extracting(ex -> ((ApiException) ex).getCode())
			.isEqualTo(ErrorCode.EMPLOYEE_NUMBER_TAKEN);
	}

	/** Any other integrity failure is not hidden. */
	@Test
	void rethrowsOtherIntegrityFailures() {
		DataIntegrityViolationException failure = new DataIntegrityViolationException("insert",
				new SQLException("duplicate key value violates unique constraint \"employee_email_uk\""));
		when(employees.saveAndFlush(any())).thenThrow(failure);

		assertThatThrownBy(() -> service.register(command)).isSameAs(failure);
	}

}
