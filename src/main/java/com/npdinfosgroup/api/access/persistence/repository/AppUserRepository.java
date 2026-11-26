package com.npdinfosgroup.api.access.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/**
	 * The users with some emails, to show the name of who created each user of a page.
	 *
	 * @param emails emails in lower case, as they are stored
	 * @return the users found
	 */
	List<AppUser> findByEmailIn(Collection<String> emails);

	/**
	 * A user, locked until the transaction ends, to edit it.
	 *
	 * @param id user id
	 * @return the user, if it exists
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM AppUser u WHERE u.id = :id")
	Optional<AppUser> lockById(@Param("id") long id);

	/**
	 * Records an access: the moment, and {@code PENDING} becomes {@code ACTIVE}; skipped after a recent one.
	 *
	 * @param keycloakId Keycloak id of the user
	 * @param now moment of the access
	 * @param since only an older last access (or none) is replaced
	 * @return 1 if it was recorded, 0 if the user is not in {@code app_user} or accessed after {@code since}
	 */
	@Modifying
	@Query("""
			UPDATE AppUser u
			SET u.lastAccessAt = :now,
				u.state = CASE WHEN u.state = com.npdinfosgroup.api.access.persistence.entity.AppUserState.PENDING
					THEN com.npdinfosgroup.api.access.persistence.entity.AppUserState.ACTIVE ELSE u.state END
			WHERE u.keycloakId = :keycloakId AND (u.lastAccessAt IS NULL OR u.lastAccessAt < :since)""")
	int recordAccess(@Param("keycloakId") UUID keycloakId, @Param("now") Instant now, @Param("since") Instant since);

}
