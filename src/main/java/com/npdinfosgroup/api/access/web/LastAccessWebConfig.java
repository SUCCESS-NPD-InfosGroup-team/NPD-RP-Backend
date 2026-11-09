package com.npdinfosgroup.api.access.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link LastAccessInterceptor} on the API routes.
 */
@Configuration(proxyBeanMethods = false)
class LastAccessWebConfig implements WebMvcConfigurer {

	private final LastAccessInterceptor interceptor;

	/**
	 * Creates the configuration.
	 *
	 * @param interceptor records the last access
	 */
	LastAccessWebConfig(LastAccessInterceptor interceptor) {
		this.interceptor = interceptor;
	}

	/**
	 * Adds {@link LastAccessInterceptor} to every {@code /api/} route.
	 * @param registry the interceptors of Spring MVC
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(interceptor).addPathPatterns("/api/**");
	}

}
