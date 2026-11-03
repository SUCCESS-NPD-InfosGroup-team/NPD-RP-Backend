package com.npdinfosgroup.api.access.service;

import com.npdinfosgroup.api.access.dto.response.UserResponse;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
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
 * Reads the users created through the platform, for the user list (CA-09). It only reads
 * {@code app_user}, so it works when Keycloak is down.
 */
@Service
public class UserQueryService {

	/** By full name; the id breaks ties so the pages do not overlap. */
	private static final Sort LIST_ORDER = Sort.by("fullName", "id");

	private final AppUserRepository users;

	/**
	 * Creates the service.
	 *
	 * @param users the users
	 */
	UserQueryService(AppUserRepository users) {
		this.users = users;
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
		return new PageResponse<>(page.getContent().stream().map(UserQueryService::toResponse).toList(),
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
		return users.findById(id).map(UserQueryService::toResponse).orElseThrow(() -> new NotFoundException(id));
	}

	/**
	 * The API form of a user, shared with {@link UserRegistrationService}.
	 *
	 * @param user the saved user
	 * @return its response
	 */
	static UserResponse toResponse(AppUser user) {
		return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getState().name(),
				user.getLastAccessAt());
	}

}
