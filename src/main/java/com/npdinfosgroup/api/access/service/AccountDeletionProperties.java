package com.npdinfosgroup.api.access.service;

import java.time.Duration;
import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * When a deactivated account is deleted ({@code app.access.*}, {@code docs/specs/SCRUM-231/design.md} §7).
 * The application does not start if one is missing.
 *
 * @param accountDeletionDelay time from the deactivation to the deletion: one day, shorter only in tests
 * @param accountDeletionRetryDelay wait before retrying a deletion that failed, for example because
 *        Keycloak was down
 */
@Validated
@ConfigurationProperties("app.access")
public record AccountDeletionProperties(@NotNull Duration accountDeletionDelay,
		@NotNull Duration accountDeletionRetryDelay) {

	/**
	 * When a deactivated account is deleted.
	 *
	 * @param disabledAt moment of the deactivation
	 * @return the deletion time, or {@code null} if the account is not deactivated
	 */
	public Instant deletesAt(Instant disabledAt) {
		return disabledAt == null ? null : disabledAt.plus(accountDeletionDelay);
	}

}
