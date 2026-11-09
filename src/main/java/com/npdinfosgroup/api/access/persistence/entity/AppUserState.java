package com.npdinfosgroup.api.access.persistence.entity;

/**
 * Whether a user created through the platform has started using the account: {@code PENDING} until
 * their first API call, then {@code ACTIVE}. {@code DISABLED} is reserved for a future story.
 */
public enum AppUserState {

	PENDING, ACTIVE, DISABLED

}
