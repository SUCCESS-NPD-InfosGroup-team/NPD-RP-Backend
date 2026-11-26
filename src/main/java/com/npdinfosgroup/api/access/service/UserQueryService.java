package com.npdinfosgroup.api.access.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.access.dto.response.UserEmployeeResponse;
import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.planner.dto.response.NpdLeadEmployeeSummary;
import com.npdinfosgroup.api.planner.service.NpdLeadEmployeeService;
import com.npdinfosgroup.api.shared.dto.PageInfo;
import com.npdinfosgroup.api.shared.dto.PageResponse;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the users created through the platform, for the user list and the edit modal (SCRUM-93 CA-09,
 * SCRUM-53 CA-09). It only reads the database, so it works when Keycloak is down.
 */
@Service
public class UserQueryService {

	/** By full name; the id breaks ties so the pages do not overlap. */
	private static final Sort LIST_ORDER = Sort.by("fullName", "id");

	private final AppUserRepository users;

	private final NpdLeadEmployeeService npdLeads;

	/**
	 * Creates the service.
	 *
	 * @param users the users
	 * @param npdLeads the employee records linked to NPD lead users
	 */
	UserQueryService(AppUserRepository users, NpdLeadEmployeeService npdLeads) {
		this.users = users;
		this.npdLeads = npdLeads;
	}

	/**
	 * A page of the users, sorted by full name, without filters.
	 *
	 * @param pageable page number and size; its sort is ignored, the order is always the same
	 * @return the page
	 */
	@Transactional(readOnly = true)
	public PageResponse<UserResponse> list(Pageable pageable) {
		Page<AppUser> page = users.findAll(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER));
		return new PageResponse<>(toResponses(page.getContent()),
				new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
	}

	/**
	 * One user.
	 *
	 * @param id user id
	 * @return the user
	 * @throws NotFoundException if there is no user with that id
	 */
	@Transactional(readOnly = true)
	public UserResponse get(long id) {
		return toResponse(users.findById(id).orElseThrow(() -> new NotFoundException(id)));
	}

	/**
	 * The API form of a user, shared with {@link UserRegistrationService}.
	 *
	 * @param user the saved user
	 * @return its response
	 */
	@Transactional(readOnly = true)
	public UserResponse toResponse(AppUser user) {
		return toResponses(List.of(user)).getFirst();
	}

	/**
	 * The API form of some users. The names of their creators and their employee records are read with
	 * one query each, whatever the number of users.
	 */
	private List<UserResponse> toResponses(List<AppUser> page) {
		Map<String, String> creators = creatorNames(page);
		Map<Long, NpdLeadEmployeeSummary> employees = npdLeads.summaries(
				page.stream().map(AppUser::getEmployeeId).filter(Objects::nonNull).toList());
		return page.stream()
			.map(user -> new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
					user.getState().name(), user.getLastAccessAt(), user.getCreatedAt(),
					creators.getOrDefault(user.getCreatedBy().toLowerCase(Locale.ROOT), user.getCreatedBy()),
					user.getVersion(), toEmployee(user.getEmployeeId(), employees)))
			.toList();
	}

	/**
	 * Full name of each creator that is a user too, by email. {@code created_by} keeps the username of the
	 * token, which is the email; a creator that is not in {@code app_user} (an account made in the Keycloak
	 * console) keeps the username.
	 */
	private Map<String, String> creatorNames(List<AppUser> page) {
		List<String> emails = page.stream().map(user -> user.getCreatedBy().toLowerCase(Locale.ROOT)).distinct().toList();
		if (emails.isEmpty()) {
			return Map.of();
		}
		return users.findByEmailIn(emails)
			.stream()
			.collect(Collectors.toMap(AppUser::getEmail, AppUser::getFullName, (first, second) -> first));
	}

	/** The linked employee record; {@code null} when the user has none (every role but NPD lead). */
	private static UserEmployeeResponse toEmployee(Long employeeId, Map<Long, NpdLeadEmployeeSummary> employees) {
		// Map.of() rejects a null key, so a user without a record is answered before looking it up
		NpdLeadEmployeeSummary employee = employeeId == null ? null : employees.get(employeeId);
		return employee == null ? null
				: new UserEmployeeResponse(employee.id(), employee.employeeNumber(), employee.status(),
						employee.collaborators());
	}

}
