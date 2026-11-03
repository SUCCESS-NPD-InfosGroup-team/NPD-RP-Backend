package com.npdinfosgroup.api.access.persistence.repository;

import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Users created through the platform.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	/**
	 * Whether a user already has that email.
	 *
	 * @param email email in lower case, as it is stored
	 * @return {@code true} if it is taken
	 */
	boolean existsByEmail(String email);

}
