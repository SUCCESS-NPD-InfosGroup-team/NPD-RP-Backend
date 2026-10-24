package com.npdinfosgroup.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * How the Keycloak token becomes the user: realm roles as authorities and
 * {@code preferred_username} as the name.
 */
class SecurityConfigTest {

	/** Leaves no user behind for other tests. */
	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	/** Each realm role becomes a {@code ROLE_} authority, and the name is the preferred username. */
	@Test
	void readsRealmRolesAndUsername() {
		Jwt jwt = token(Map.of("roles", List.of("operations-administrator", "guest")));

		AbstractAuthenticationToken authentication = SecurityConfig.jwtAuthenticationConverter().convert(jwt);

		assertThat(authentication.getName()).isEqualTo("mperez");
		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.contains("ROLE_operations-administrator", "ROLE_guest")
			.filteredOn(authority -> authority.startsWith("ROLE_"))
			.hasSize(2);
	}

	/** A token without realm roles grants nothing. */
	@Test
	void grantsNothingWithoutRealmRoles() {
		assertThat(SecurityConfig.realmRoles(token(null))).isEmpty();
		assertThat(SecurityConfig.realmRoles(token(Map.of("roles", "not-a-list")))).isEmpty();
	}

	/** The current user is the authenticated name; without one it is a programming error. */
	@Test
	void currentUserIsTheAuthenticatedName() {
		CurrentUser currentUser = new CurrentUser();
		assertThatIllegalStateException().isThrownBy(currentUser::username);

		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken("mperez", null, "ROLE_guest"));
		assertThat(currentUser.username()).isEqualTo("mperez");
	}

	private static Jwt token(Map<String, Object> realmAccess) {
		Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").claim("preferred_username", "mperez");
		if (realmAccess != null) {
			builder.claim("realm_access", realmAccess);
		}
		return builder.build();
	}

}
