package com.npdinfosgroup.api.access.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

/**
 * Registers {@link KeycloakSessionValidator}, so the API rejects access tokens whose Keycloak session
 * has ended (see {@code docs/specs/SCRUM-159/design.md}).
 * <p>
 * Spring Boot adds every {@link OAuth2TokenValidator} of {@link Jwt} bean to the decoder it builds from
 * {@code spring.security.oauth2.resourceserver.jwt.*}, so the security configuration does not change.
 * The introspection client is not a bean: it is only used by the validator, and the admin client stays
 * the only {@link RestClient} bean of Keycloak.
 */
@Configuration(proxyBeanMethods = false)
class KeycloakIntrospectionConfig {

	/**
	 * The validator, calling Keycloak with the timeouts of the settings.
	 *
	 * @param properties Keycloak settings
	 * @return the validator Spring Boot adds to the JWT decoder
	 */
	@Bean
	OAuth2TokenValidator<Jwt> keycloakSessionValidator(KeycloakAdminProperties properties) {
		RestClient.Builder builder = RestClient.builder()
			.requestFactory(KeycloakAdminClientConfig.requestFactory(properties));
		return new KeycloakSessionValidator(create(builder, properties));
	}

	/**
	 * Builds the introspection client on the given builder, so tests can bind a mock server to it.
	 * Introspection authenticates with the service account's id and secret (HTTP Basic), not with a
	 * bearer token.
	 *
	 * @param builder builder with the HTTP settings; it is copied, not changed
	 * @param properties Keycloak settings
	 * @return the client, with the realm's introspection endpoint as base URL
	 */
	static RestClient create(RestClient.Builder builder, KeycloakAdminProperties properties) {
		return builder.clone()
			.baseUrl(properties.introspectionUri())
			.defaultHeaders(headers -> headers.setBasicAuth(properties.adminClientId(), properties.adminClientSecret()))
			.build();
	}

}
