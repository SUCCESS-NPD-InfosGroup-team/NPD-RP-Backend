package com.npdinfosgroup.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Replaces Keycloak in the tests: the token is the name of a realm role, and the decoder turns it into
 * a JWT with that role and the username {@code <role>.user}.
 * <p>
 * So {@code Authorization: Bearer operations-administrator} acts as an Operations administrator, and
 * {@link #INVALID_TOKEN} is rejected like an expired token. Used with
 * {@code @Import(TestSecurityConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

	/** A token the decoder rejects. */
	public static final String INVALID_TOKEN = "invalid";

	/**
	 * Username of the user behind a test token.
	 *
	 * @param role realm role used as the token
	 * @return the {@code preferred_username} of the token
	 */
	public static String usernameOf(String role) {
		return role + ".user";
	}

	/**
	 * Decoder that accepts any role name as the token.
	 *
	 * @return the decoder
	 */
	@Bean
	JwtDecoder jwtDecoder() {
		return token -> {
			if (INVALID_TOKEN.equals(token)) {
				throw new BadJwtException("Invalid test token");
			}
			Instant now = Instant.now();
			return Jwt.withTokenValue(token)
				.header("alg", "none")
				.subject(token)
				.claim("preferred_username", usernameOf(token))
				.claim("realm_access", Map.of("roles", List.of(token)))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.build();
		};
	}

}
