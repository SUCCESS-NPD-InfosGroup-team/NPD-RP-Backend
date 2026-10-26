package com.npdinfosgroup.api.shared.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clock of the application. Rules such as "the end date has already passed" ask it for today, so
 * tests can fix the date.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

	/** The business and its users are in Colombia; "today" follows their calendar, not the server's. */
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

	/**
	 * System clock in the business time zone.
	 *
	 * @return the clock
	 */
	@Bean
	Clock clock() {
		return Clock.system(BUSINESS_ZONE);
	}

}
