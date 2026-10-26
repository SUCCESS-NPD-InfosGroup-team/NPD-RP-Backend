package com.npdinfosgroup.api.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API versioning (Spring Framework 7): the version is path segment 1 of the API routes, as in
 * {@code /api/v1/resources}, and controllers declare it with the {@code version} attribute of their
 * mappings.
 * <p>
 * Set here instead of with {@code spring.mvc.apiversion.use.path-segment} because the version is only
 * read under {@code /api/}: other routes, such as Scalar UI, have no version segment and would be
 * rejected. The supported versions stay in {@code application.properties}.
 */
@Configuration(proxyBeanMethods = false)
class ApiVersionConfig implements WebMvcConfigurer {

	/**
	 * Reads the version from path segment 1 of the {@code /api/} routes only.
	 *
	 * @param configurer API versioning settings
	 */
	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		configurer.usePathSegment(1, path -> path.value().startsWith("/api/"));
	}

}
