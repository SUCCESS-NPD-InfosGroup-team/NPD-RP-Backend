package com.npdinfosgroup.api.notifications.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * Tells when the next e-mail notices will be sent, from the configured schedule. It does not send
 * anything: that is the notice job (SCRUM-27).
 */
@Service
public class NoticeScheduleService {

	private final NoticeScheduleProperties properties;

	private final Clock clock;

	/**
	 * Creates the service.
	 *
	 * @param properties the schedule of the notices
	 * @param clock the application clock, in the business time zone (America/Bogota), where the cron
	 *        times are read
	 */
	NoticeScheduleService(NoticeScheduleProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * The next moment the notices will be sent, for «Próximo envío de avisos» of the status bar (HU-11).
	 *
	 * @return the next send after now, or empty while no send is scheduled
	 */
	public Optional<Instant> nextSend() {
		if (!properties.enabled()) {
			return Optional.empty();
		}
		ZonedDateTime next = CronExpression.parse(properties.noticeCron()).next(ZonedDateTime.now(clock));
		return Optional.ofNullable(next).map(ZonedDateTime::toInstant);
	}

}
