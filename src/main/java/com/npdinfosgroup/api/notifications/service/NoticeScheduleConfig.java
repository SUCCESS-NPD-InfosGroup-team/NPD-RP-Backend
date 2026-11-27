package com.npdinfosgroup.api.notifications.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the schedule of the e-mail notices.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NoticeScheduleProperties.class)
class NoticeScheduleConfig {

}
