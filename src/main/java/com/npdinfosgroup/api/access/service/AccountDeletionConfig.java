package com.npdinfosgroup.api.access.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the settings of the deletion of deactivated accounts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountDeletionProperties.class)
class AccountDeletionConfig {

}
