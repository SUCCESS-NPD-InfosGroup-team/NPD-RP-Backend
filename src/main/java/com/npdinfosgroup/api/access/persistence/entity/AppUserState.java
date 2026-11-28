package com.npdinfosgroup.api.access.persistence.entity;

/**
 * Whether a user created through the platform has started using the account: {@code PENDING} until
 * their first API call, then {@code ACTIVE}. A user who leaves the platform is deleted (SCRUM-231).
 */
public enum AppUserState {

	PENDING, ACTIVE

}
