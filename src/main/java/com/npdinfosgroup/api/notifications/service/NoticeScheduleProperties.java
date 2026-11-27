package com.npdinfosgroup.api.notifications.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

/**
 * When the e-mail notices are sent ({@code app.notifications.*}, {@code docs/specs/SCRUM-224/design.md}
 * Decision 4). The status bar reads it to show the next send, and the notice job (SCRUM-27) will run on
 * it, so both always agree.
 * <p>
 * The application does not start if the value is not a valid Spring cron expression or {@code -}.
 *
 * @param noticeCron Spring cron expression in America/Bogota time, or {@code -} ({@link Scheduled#CRON_DISABLED})
 *        while no send is scheduled
 */
@Validated
@ConfigurationProperties("app.notifications")
public record NoticeScheduleProperties(@NotBlank String noticeCron) {

	/**
	 * Rejects an expression that is neither a valid cron nor {@code -}, so a typo stops the startup
	 * instead of reaching the status bar.
	 *
	 * @param noticeCron the configured expression
	 * @throws IllegalArgumentException if it is not a valid cron expression
	 */
	public NoticeScheduleProperties {
		if (noticeCron != null && !noticeCron.isBlank() && !Scheduled.CRON_DISABLED.equals(noticeCron)
				&& !CronExpression.isValidExpression(noticeCron)) {
			throw new IllegalArgumentException("app.notifications.notice-cron is not a valid cron expression: " + noticeCron);
		}
	}

	/**
	 * Whether a send is scheduled.
	 *
	 * @return {@code false} when the value is {@code -}
	 */
	public boolean enabled() {
		return !Scheduled.CRON_DISABLED.equals(noticeCron);
	}

}
