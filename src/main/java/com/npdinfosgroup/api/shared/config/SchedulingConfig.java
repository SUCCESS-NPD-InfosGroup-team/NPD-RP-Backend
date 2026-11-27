package com.npdinfosgroup.api.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's task scheduling, so Spring Boot provides the {@code TaskScheduler} that runs the
 * scheduled jobs of the modules (for example, the deletion of deactivated accounts, SCRUM-231).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {

}
