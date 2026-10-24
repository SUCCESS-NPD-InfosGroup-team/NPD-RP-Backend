package com.npdinfosgroup.api.shared.security;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers 401 (no valid token) and 403 (role without permission) as ProblemDetails, like every
 * other error of the API.
 * <p>
 * These errors happen in the security filters, before the request reaches a controller, so
 * {@code GlobalExceptionHandler} never sees them.
 */
@Component
class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ProblemDetailFactory problems;

	private final LocaleResolver localeResolver;

	private final JsonMapper jsonMapper;

	/**
	 * Creates the handler.
	 *
	 * @param problems builder of the response bodies
	 * @param localeResolver resolves the language from {@code Accept-Language}
	 * @param jsonMapper writes the body
	 */
	ProblemDetailsSecurityHandler(ProblemDetailFactory problems, LocaleResolver localeResolver,
			JsonMapper jsonMapper) {
		this.problems = problems;
		this.localeResolver = localeResolver;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Answers 401 when the token is missing, invalid or expired.
	 *
	 * @param request current request
	 * @param response response to write
	 * @param authException why authentication failed
	 * @throws IOException if the body cannot be written
	 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		write(request, response, ErrorCode.UNAUTHORIZED);
	}

	/**
	 * Answers 403 when the user's role does not allow the operation.
	 *
	 * @param request current request
	 * @param response response to write
	 * @param accessDeniedException the denial
	 * @throws IOException if the body cannot be written
	 */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(request, response, ErrorCode.FORBIDDEN);
	}

	private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
		ProblemDetail problem = problems.create(code, List.of(), localeResolver.resolveLocale(request));
		problem.setInstance(URI.create(request.getRequestURI()));
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), problem);
	}

}
