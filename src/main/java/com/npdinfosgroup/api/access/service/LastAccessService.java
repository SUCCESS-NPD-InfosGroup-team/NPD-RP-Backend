package com.npdinfosgroup.api.access.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the state and the last access of the users created through the platform (design §5): a user
 * becomes {@code ACTIVE} on their first API call, and {@code last_access_at} is that call's moment.
 * <p>
 * Written at most once every {@link #INTERVAL} per user: this instance remembers when it last recorded
 * each one, and the update itself skips a recent access, which covers other instances of the API.
 */
@Service
public class LastAccessService {

	/** How often the last access of a user is written at most. */
	static final Duration INTERVAL = Duration.ofMinutes(5);

	private final AppUserRepository users;

	private final Clock clock;

	private final Map<UUID, Instant> lastRecorded = new ConcurrentHashMap<>();

	/**
	 * Creates the service.
	 *
	 * @param users the users
	 * @param clock gives the moment of each access
	 */
	LastAccessService(AppUserRepository users, Clock clock) {
		this.users = users;
		this.clock = clock;
	}

	/**
	 * Records that a user called the API; users not in {@code app_user} are ignored.
	 *
	 * @param keycloakId Keycloak id of the user: the {@code sub} claim of the token
	 */
	@Transactional
	public void recordAccess(UUID keycloakId) {
		Instant now = clock.instant();
		Instant since = now.minus(INTERVAL);
		Instant previous = lastRecorded.get(keycloakId);
		if (previous != null && previous.isAfter(since)) {
			return;
		}
		lastRecorded.put(keycloakId, now);
		users.recordAccess(keycloakId, now, since);
	}

}
