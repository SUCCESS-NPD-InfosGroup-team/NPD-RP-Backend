package com.npdinfosgroup.api.access.persistence.entity;

/**
 * Whether a user created through the platform has started using the account: {@code PENDING} until
 * their first API call, then {@code ACTIVE}. {@code DISABLED} while the account is deactivated, until it is
 * reactivated or deleted (SCRUM-231).
 */
public enum AppUserState {

	PENDING, ACTIVE, DISABLED

}
