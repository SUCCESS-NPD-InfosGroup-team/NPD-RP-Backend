package com.npdinfosgroup.api.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is making the current request, as recorded in the audit log.
 */
@Component
public class CurrentUser {

	/**
	 * Username of the authenticated user: the {@code preferred_username} claim of the Keycloak token,
	 * which {@link SecurityConfig} sets as the principal name.
	 *
	 * @return the username
	 * @throws IllegalStateException if the request is not authenticated, which the security rules
	 *         should have prevented
	 */
	public String username() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new IllegalStateException("No authenticated user in the security context");
		}
		return authentication.getName();
	}

}
