package com.npdinfosgroup.api.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security of the API: every call needs a Keycloak access token (JWT), validated against the issuer
 * in {@code KEYCLOAK_ISSUER_URI}.
 * <p>
 * Roles come from the token's {@code realm_access.roles} claim and permissions are checked here, by
 * route and method, so a hidden button in the frontend is never the only barrier. Only the health
 * check and, in dev mode, the API docs are public.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	private static final String ROLE_PREFIX = "ROLE_";

	/**
	 * Rules of each route, a stateless session and ProblemDetails for 401 and 403.
	 *
	 * @param http security builder
	 * @param errorHandler writes the 401 and 403 responses
	 * @return the filter chain
	 * @throws Exception if the chain cannot be built
	 */
	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailsSecurityHandler errorHandler) throws Exception {
		String operationsAdministrator = Role.OPERATIONS_ADMINISTRATOR.realmName();
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				// The docs are off outside APP_DEV_MODE, so these routes answer 404 there
				.requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/scalar/**", "/error").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/*/resources").hasRole(operationsAdministrator)
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint(errorHandler)
				.accessDeniedHandler(errorHandler))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(errorHandler)
				.accessDeniedHandler(errorHandler));
		return http.build();
	}

	/**
	 * Reads the user from the token: the name is {@code preferred_username} (what the audit log
	 * records) and the authorities are the realm roles.
	 *
	 * @return the converter
	 */
	static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setPrincipalClaimName("preferred_username");
		converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRoles);
		return converter;
	}

	/**
	 * Turns the realm roles of the token into {@code ROLE_<name>} authorities.
	 *
	 * @param jwt the token
	 * @return one authority per realm role; none when the claim is missing
	 */
	static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
		if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
			return List.of();
		}
		return roles.stream()
			.map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
			.toList();
	}

}
