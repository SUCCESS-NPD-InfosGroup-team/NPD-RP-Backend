package com.npdinfosgroup.api.planner.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.npdinfosgroup.api.planner.dto.request.AssignMembersRequest;
import com.npdinfosgroup.api.planner.dto.request.MemberAssignment;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.entity.Project;
import com.npdinfosgroup.api.planner.persistence.repository.EmployeeRepository;
import com.npdinfosgroup.api.planner.service.CatalogService;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Who a bulk assignment can add or move to a project, without a database: the size of the list and the
 * problems of each row, with the person and its position.
 */
@ExtendWith(MockitoExtension.class)
class MemberAssignmentValidatorTest {

	private static final short ACTIVE = 1;

	private static final short TERMINATED = 5;

	@Mock
	private EmployeeRepository employees;

	@Mock
	private CatalogService catalogs;

	private MemberAssignmentValidator validator;

	private Project project;

	private List<Employee> people;

	/** Project 7, and people 1 to 4: 1 and 2 without project, 3 on Baja, 4 already in project 7. */
	@BeforeEach
	void setUp() {
		validator = new MemberAssignmentValidator(employees, catalogs);
		project = new Project();
		project.setId(7L);
		people = new ArrayList<>(List.of(person(1, ACTIVE, null), person(2, ACTIVE, 12L), person(3, TERMINATED, null),
				person(4, ACTIVE, 7L)));
		lenient().when(employees.findAllById(anyCollection()))
			.thenAnswer(call -> people.stream().filter(p -> call.<Collection<Long>>getArgument(0).contains(p.getId())).toList());
		lenient().when(catalogs.findStatusId("BAJA")).thenReturn(Optional.of(TERMINATED));
	}

	/** People without a project and people from another project are returned in the order of the request. */
	@Test
	void returnsThePeopleInOrder() {
		assertThat(validator.validate(project, request(member(2L, 5), member(1L, 0))))
			.extracting(Employee::getId)
			.containsExactly(2L, 1L);
	}

	/** An empty list and a list over 100 people are rejected before reading anyone. */
	@Test
	void rejectsAnEmptyOrTooLongList() {
		assertThat(problems(new AssignMembersRequest(null, null)))
			.containsExactly(tuple("members", FieldErrorCode.REQUIRED, Map.of()));
		assertThat(problems(new AssignMembersRequest(null, Collections.nCopies(101, member(1L, 0)))))
			.containsExactly(tuple("members", FieldErrorCode.TOO_MANY, Map.of()));
		verifyNoInteractions(employees);
	}

	/** Each person that cannot be assigned is reported on its row, with its id and position. */
	@Test
	void reportsThePeopleWhoCannotBeAssigned() {
		assertThat(problems(request(member(null, 0), member(99L, 0), member(3L, 0), member(4L, 0), member(1L, 0),
				member(1L, 0), member(2L, null))))
			.containsExactly(tuple("employeeId", FieldErrorCode.REQUIRED, Map.of("index", 0)),
					tuple("employeeId", FieldErrorCode.UNKNOWN_ID, Map.of("employeeId", 99L, "index", 1)),
					tuple("employeeId", FieldErrorCode.TERMINATED, Map.of("employeeId", 3L, "index", 2)),
					tuple("employeeId", FieldErrorCode.ALREADY_IN_PROJECT, Map.of("employeeId", 4L, "index", 3)),
					tuple("employeeId", FieldErrorCode.DUPLICATED, Map.of("employeeId", 1L, "index", 5)),
					tuple("version", FieldErrorCode.REQUIRED, Map.of("employeeId", 2L, "index", 6)));
	}

	private List<Tuple> problems(AssignMembersRequest request) {
		ValidationException error = catchThrowableOfType(ValidationException.class,
				() -> validator.validate(project, request));
		assertThat(error).isNotNull();
		assertThat(error.getScope()).isEqualTo("members");
		return error.getProblems().stream().map(problem -> tuple(problem.field(), problem.code(), problem.details())).toList();
	}

	private static AssignMembersRequest request(MemberAssignment... members) {
		return new AssignMembersRequest(null, List.of(members));
	}

	private static MemberAssignment member(Long id, Integer version) {
		return new MemberAssignment(id, version, null, null, null, null, null);
	}

	private static Employee person(long id, short status, Long projectId) {
		Employee employee = new Employee();
		employee.setId(id);
		employee.setStatusId(status);
		employee.setProjectId(projectId);
		return employee;
	}

}
