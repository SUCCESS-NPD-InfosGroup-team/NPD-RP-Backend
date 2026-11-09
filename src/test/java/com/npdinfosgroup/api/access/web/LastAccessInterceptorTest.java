package com.npdinfosgroup.api.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import com.npdinfosgroup.api.access.service.LastAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Recording the last access never stops a request, whatever the token or the database do.
 */
class LastAccessInterceptorTest {

	private final LastAccessService lastAccess = mock();

	private final LastAccessInterceptor interceptor = new LastAccessInterceptor(lastAccess);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	/** A token without subject (a service account) records nothing; a database failure is only logged. */
	@Test
	void neverStopsTheRequest() {
		authenticate(Jwt.withTokenValue("t").header("alg", "none").claim("azp", "npd-rp-web").build());
		assertThat(preHandle()).isTrue();
		verifyNoInteractions(lastAccess);

		doThrow(new QueryTimeoutException("timeout")).when(lastAccess).recordAccess(any());
		authenticate(Jwt.withTokenValue("t").header("alg", "none").subject(UUID.randomUUID().toString()).build());
		assertThat(preHandle()).isTrue();
	}

	private void authenticate(Jwt jwt) {
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	private boolean preHandle() {
		return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
	}

}
