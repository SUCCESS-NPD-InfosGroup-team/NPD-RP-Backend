package com.npdinfosgroup.api.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * The next send of the e-mail notices (HU-11, design Decision 4), without Spring: the disabled schedule,
 * the cron read in Bogota time and the rejected expressions.
 */
class NoticeScheduleServiceTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

	/** 06:30 in Bogota, before the 07:00 send of the day. */
	private static final Clock BEFORE_SEND = Clock.fixed(TODAY.atTime(6, 30).atZone(BOGOTA).toInstant(), BOGOTA);

	/** 23:30 in Bogota, already the next day in UTC and after the 07:00 send of the day. */
	private static final Clock AFTER_SEND = Clock.fixed(TODAY.atTime(23, 30).atZone(BOGOTA).toInstant(), BOGOTA);

	private static final String EVERY_DAY_AT_7 = "0 0 7 * * *";

	/** «-» means no send is scheduled: the bar shows «—». */
	@Test
	void hasNoNextSendWhileDisabled() {
		NoticeScheduleService service = new NoticeScheduleService(new NoticeScheduleProperties("-"), BEFORE_SEND);

		assertThat(service.nextSend()).isEmpty();
	}

	/** The cron hours are Bogota hours, not the server's. */
	@Test
	void givesTheSendOfTodayInBogotaTime() {
		NoticeScheduleService service = new NoticeScheduleService(new NoticeScheduleProperties(EVERY_DAY_AT_7),
				BEFORE_SEND);

		assertThat(service.nextSend()).contains(TODAY.atTime(7, 0).atZone(BOGOTA).toInstant());
	}

	/** Once today's send passed, the next one is tomorrow's, even when UTC is already on the next day. */
	@Test
	void givesTheSendOfTomorrowOnceTodaysPassed() {
		NoticeScheduleService service = new NoticeScheduleService(new NoticeScheduleProperties(EVERY_DAY_AT_7),
				AFTER_SEND);

		assertThat(service.nextSend()).contains(TODAY.plusDays(1).atTime(7, 0).atZone(BOGOTA).toInstant());
	}

	/** A typo in the setting stops the startup instead of reaching the bar. */
	@Test
	void rejectsAnInvalidExpression() {
		assertThatThrownBy(() -> new NoticeScheduleProperties("every day at 7"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("notice-cron");
	}

	/** «-» is the only value that disables it. */
	@Test
	void isEnabledOnlyWithACronExpression() {
		assertThat(new NoticeScheduleProperties("-").enabled()).isFalse();
		assertThat(new NoticeScheduleProperties(EVERY_DAY_AT_7).enabled()).isTrue();
	}

}
