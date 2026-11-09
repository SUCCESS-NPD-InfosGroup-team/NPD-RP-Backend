package com.npdinfosgroup.api.access.client;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * HTTP client of the Keycloak Admin REST API. Every request carries a token of the API's own service
 * account ({@code client_credentials}), which Spring Security gets on the first call and reuses until it
 * is about to expire.
 * <p>
 * The registration is built here instead of with {@code spring.security.oauth2.client.*}, so Spring
 * Boot adds no OAuth2 login to the API and the client also exists without the web server.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KeycloakAdminProperties.class)
class KeycloakAdminClientConfig {

	private static final String REGISTRATION_ID = "keycloak-admin";

	/**
	 * The token belongs to the API, not to the user making the request: with one fixed principal every
	 * request shares the same cached token.
	 */
	private static final Authentication SERVICE_ACCOUNT = UsernamePasswordAuthenticationToken.unauthenticated("npd-rp-api",
			null);

	/**
	 * Client used by {@code access/client}, with the connect and read timeouts of the settings for both
	 * the admin calls and the token requests.
	 *
	 * @param properties Keycloak settings
	 * @return the client, with the admin URL of the realm as base URL
	 */
	@Bean
	RestClient keycloakAdminRestClient(KeycloakAdminProperties properties) {
		return create(RestClient.builder().requestFactory(requestFactory(properties)), properties);
	}

	/**
	 * HTTP settings of every call to Keycloak: the admin calls, the token requests and the token
	 * introspection ({@link KeycloakIntrospectionConfig}).
	 *
	 * @param properties Keycloak settings
	 * @return a request factory with the connect and read timeouts of the settings
	 */
	static JdkClientHttpRequestFactory requestFactory(KeycloakAdminProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return requestFactory;
	}

	/**
	 * Builds the client on the given builder, so tests can bind a mock server to it.
	 *
	 * @param builder builder with the HTTP settings; it is copied, not changed
	 * @param properties Keycloak settings
	 * @return the client, with the admin URL of the realm as base URL
	 */
	static RestClient create(RestClient.Builder builder, KeycloakAdminProperties properties) {
		ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.clientId(properties.adminClientId())
			.clientSecret(properties.adminClientSecret())
			.tokenUri(properties.tokenUri().toString())
			.build();
		InMemoryClientRegistrationRepository registrations = new InMemoryClientRegistrationRepository(registration);
		// The service-based manager works outside a web request and keeps the token between requests
		AuthorizedClientServiceOAuth2AuthorizedClientManager manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
				registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
		manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
			.clientCredentials(credentials -> credentials.accessTokenResponseClient(tokenClient(builder.clone())))
			.build());

		OAuth2ClientHttpRequestInterceptor oauth2 = new OAuth2ClientHttpRequestInterceptor(manager);
		oauth2.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
		oauth2.setPrincipalResolver(request -> SERVICE_ACCOUNT);
		return builder.clone().baseUrl(properties.adminUri()).requestInterceptor(oauth2).build();
	}

	/**
	 * Token requests with the same HTTP settings as the admin calls; the converters and error handler
	 * are the ones Spring Security uses by default.
	 */
	private static RestClientClientCredentialsTokenResponseClient tokenClient(RestClient.Builder builder) {
		RestClientClientCredentialsTokenResponseClient tokenClient = new RestClientClientCredentialsTokenResponseClient();
		tokenClient.setRestClient(builder
			.configureMessageConverters(converters -> converters.addCustomConverter(new FormHttpMessageConverter())
				.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
			.defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
			.build());
		return tokenClient;
	}

}
