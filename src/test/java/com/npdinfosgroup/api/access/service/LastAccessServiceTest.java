package com.npdinfosgroup.api.access.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import org.junit.jupiter.api.Test;

/**
 * The last access is written at most once every five minutes per user.
 */
class LastAccessServiceTest {

	private static final UUID ANA = UUID.fromString("5f0c2b8e-1d2a-4c3b-9e4f-6a7b8c9d0e1f");

	private static final UUID LUIS = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

	private static final Instant START = Instant.parse("2026-11-02T15:00:00Z");

	private final AppUserRepository users = mock();

	private final Clock clock = mock();

	private final LastAccessService service = new LastAccessService(users, clock);

	/** Calls within five minutes are not written again; a later one is, and each user counts apart. */
	@Test
	void writesEachUserAtMostEveryFiveMinutes() {
		access(ANA, START);
		access(ANA, START.plus(Duration.ofMinutes(4)));
		access(LUIS, START.plus(Duration.ofMinutes(4)));
		access(ANA, START.plus(Duration.ofMinutes(6)));

		verify(users).recordAccess(ANA, START, START.minus(LastAccessService.INTERVAL));
		verify(users).recordAccess(LUIS, START.plus(Duration.ofMinutes(4)), START.minus(Duration.ofMinutes(1)));
		verify(users).recordAccess(ANA, START.plus(Duration.ofMinutes(6)), START.plus(Duration.ofMinutes(1)));
		verifyNoMoreInteractions(users);
	}

	private void access(UUID user, Instant at) {
		when(clock.instant()).thenReturn(at);
		service.recordAccess(user);
	}

}
