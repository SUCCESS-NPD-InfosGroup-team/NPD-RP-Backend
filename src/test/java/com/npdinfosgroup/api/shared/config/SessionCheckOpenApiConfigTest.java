package com.npdinfosgroup.api.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

/** Every operation that needs a token documents the 503 of an unchecked session, and only those. */
class SessionCheckOpenApiConfigTest {

	@Test
	void documentsTheSessionCheckOnSecuredOperationsOnly() {
		ApiResponses secured = new ApiResponses().addApiResponse("401", new ApiResponse().description("Sin token"));
		ApiResponses withDatabase = new ApiResponses().addApiResponse("401", new ApiResponse().description("Sin token"))
			.addApiResponse("503", new ApiResponse().description("La base de datos no respondió"));
		ApiResponses open = new ApiResponses().addApiResponse("200", new ApiResponse().description("OK"));
		OpenAPI openApi = new OpenAPI().paths(new Paths()
			.addPathItem("/a", new PathItem().get(new Operation().responses(secured)))
			.addPathItem("/b", new PathItem().post(new Operation().responses(withDatabase)))
			.addPathItem("/c", new PathItem().get(new Operation().responses(open))));

		SessionCheckOpenApiConfig.addSessionCheckResponse(openApi);

		assertThat(secured.get("503").getDescription()).isEqualTo(SessionCheckOpenApiConfig.SESSION_CHECK);
		assertThat(withDatabase.get("503").getDescription()).startsWith("La base de datos no respondió")
			.endsWith(SessionCheckOpenApiConfig.SESSION_CHECK);
		assertThat(open).doesNotContainKey("503");
	}

	@Test
	void acceptsASpecificationWithoutPaths() {
		SessionCheckOpenApiConfig.addSessionCheckResponse(new OpenAPI());
	}

}
