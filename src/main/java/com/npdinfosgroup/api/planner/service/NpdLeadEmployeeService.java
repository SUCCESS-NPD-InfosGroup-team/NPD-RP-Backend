package com.npdinfosgroup.api.planner.service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.response.NpdLeadEmployeeSummary;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.service.validator.ResourceRequestValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The employee record of NPD lead users, for the {@code access} module (Usuarios). Users and employees
 * are linked by {@code app_user.employee_id}; this service is the only way {@code access} reads or
 * changes the employee side. See {@code docs/specs/SCRUM-53/design.md} §4.
 */
@Service
public class NpdLeadEmployeeService {

	private final EmployeeRepository employees;

	/**
	 * Creates the service.
	 *
	 * @param employees the employee records
	 */
	NpdLeadEmployeeService(EmployeeRepository employees) {
		this.employees = employees;
	}

	/**
	 * The employee records of some users, each with how many collaborators still have them as lead (Baja
	 * excluded): what the user list shows and what must be reassigned when an NPD lead changes role.
	 *
	 * @param employeeIds ids of the linked employee records; may be empty
	 * @return the summary of each record found, by employee id
	 */
	@Transactional(readOnly = true)
	public Map<Long, NpdLeadEmployeeSummary> summaries(Collection<Long> employeeIds) {
		if (employeeIds.isEmpty()) {
			return Map.of();
		}
		return employees.findNpdLeadEmployees(employeeIds, ResourceRequestValidator.TERMINATED)
			.stream()
			.map(row -> new NpdLeadEmployeeSummary(row.id(), row.employeeNumber(), row.statusCode(),
					row.collaborators()))
			.collect(Collectors.toMap(NpdLeadEmployeeSummary::id, Function.identity()));
	}

}
