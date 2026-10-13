package com.npdinfosgroup.api.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la especificación OpenAPI que consumen los frontends a través de Scalar UI.
 * <p>
 * La especificación ({@code /v3/api-docs}) y Scalar ({@code /scalar}) solo se exponen en modo
 * desarrollo ({@code APP_DEV_MODE=true}); esa activación vive en {@code application.properties}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	/**
	 * Define el título, la descripción y la versión que muestra la documentación de la API.
	 *
	 * @return la definición base de OpenAPI que springdoc completa con los endpoints detectados
	 */
	@Bean
	OpenAPI npdRpOpenApi() {
		return new OpenAPI().info(new Info()
				.title("NPD-RP API 🐦")
				.version("v1")
				.description("""
						API de NPD-RP: reemplaza el Excel actual como única fuente de verdad. \
						Gestiona e importa sus datos y ofrece análisis estadístico, informes, \
						exportaciones y notificaciones automáticas."""));
	}

}
