package com.npdinfosgroup.api.access.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
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
	 * The users in a state, for example the deactivated ones whose deletion must be scheduled again.
	 *
	 * @param state state of the account
	 * @return the users
	 */
	List<AppUser> findByState(AppUserState state);

	/**
	 * Whether a user is already linked to an employee record.
	 *
	 * @param employeeId id of the employee record
	 * @return {@code true} if a user has it
	 */
	boolean existsByEmployeeId(Long employeeId);

	/**
	 * How many users other than one have a role and a state: the first, unlocked check that an edit does
	 * not leave the platform without an active Operations administrator.
	 *
	 * @param role realm name of the role
	 * @param state state of the account
	 * @param id the user left out
	 * @return the number of other users
	 */
	long countByRoleAndStateAndIdNot(String role, AppUserState state, Long id);

	/**
	 * The users with a role and a state, locked until the transaction ends and in id order, so two edits
	 * that lock them always do it in the same order and wait for one another instead of deadlocking.
	 *
	 * @param role realm name of the role
	 * @param state state of the account
	 * @return the users, locked
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM AppUser u WHERE u.role = :role AND u.state = :state ORDER BY u.id")
	List<AppUser> lockByRoleAndState(@Param("role") String role, @Param("state") AppUserState state);

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
