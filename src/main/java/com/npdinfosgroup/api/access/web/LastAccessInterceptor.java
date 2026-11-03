package com.npdinfosgroup.api.access.web;

import java.util.UUID;

import com.npdinfosgroup.api.access.service.LastAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Records the last access of the user behind each API call, after the security filters validated the
 * token. It is a side effect: if it fails, the request goes on and the failure is only logged.
 */
@Component
class LastAccessInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(LastAccessInterceptor.class);

	private final LastAccessService lastAccess;

	/**
	 * Creates the interceptor.
	 *
	 * @param lastAccess records the accesses
	 */
	LastAccessInterceptor(LastAccessService lastAccess) {
		this.lastAccess = lastAccess;
	}

	/**
	 * Records the access of the token's user, if its subject is a Keycloak user id.
	 *
	 * @param request the request
	 * @param response the response
	 * @param handler the controller method
	 * @return always {@code true}: the request always goes on
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
			UUID keycloakId = keycloakId(token.getToken().getSubject());
			if (keycloakId != null) {
				try {
					lastAccess.recordAccess(keycloakId);
				}
				catch (DataAccessException ex) {
					log.warn("Could not record the last access: {}", ex.getClass().getName());
				}
			}
		}
		return true;
	}

	private static UUID keycloakId(String subject) {
		if (subject == null) {
			return null;
		}
		try {
			return UUID.fromString(subject);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
