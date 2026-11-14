package com.npdinfosgroup.api.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documents the 503 {@code SESSION_CHECK_UNAVAILABLE} that every secured endpoint can answer when
 * Keycloak cannot check whether the token's session is still active (see
 * {@code docs/specs/SCRUM-159/design.md}), instead of repeating it on each controller method.
 */
@Configuration(proxyBeanMethods = false)
class SessionCheckOpenApiConfig {

	static final String SESSION_CHECK = "Keycloak no pudo verificar la sesión; se puede reintentar (`SESSION_CHECK_UNAVAILABLE`)";

	/**
	 * Adds the 503 to every operation that documents a 401, the ones that need a token.
	 *
	 * @return the customizer springdoc applies to the generated specification
	 */
	@Bean
	OpenApiCustomizer sessionCheckUnavailableResponse() {
		return SessionCheckOpenApiConfig::addSessionCheckResponse;
	}

	/**
	 * Adds the 503 response, or extends the description of the 503 an operation already has (database
	 * or Keycloak unavailable), with the same ProblemDetails content as its 401.
	 *
	 * @param openApi the generated specification
	 */
	static void addSessionCheckResponse(OpenAPI openApi) {
		if (openApi.getPaths() == null) {
			return;
		}
		openApi.getPaths()
			.values()
			.stream()
			.flatMap(path -> path.readOperations().stream())
			.map(Operation::getResponses)
			.filter(responses -> responses != null && responses.containsKey("401"))
			.forEach(SessionCheckOpenApiConfig::addTo);
	}

	private static void addTo(ApiResponses responses) {
		ApiResponse existing = responses.get("503");
		if (existing == null) {
			responses.addApiResponse("503",
					new ApiResponse().description(SESSION_CHECK).content(responses.get("401").getContent()));
		}
		else {
			existing.setDescription(existing.getDescription() + ". O bien: " + SESSION_CHECK);
		}
	}

}
