package com.npdinfosgroup.api.shared.security;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import com.npdinfosgroup.api.shared.exception.ErrorCode;
import com.npdinfosgroup.api.shared.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers 401 (no valid token) and 403 (role without permission) as ProblemDetails, like every
 * other error of the API, and 503 when Keycloak could not check whether the token's session is still
 * active (see {@code docs/specs/SCRUM-159/design.md}).
 * <p>
 * These errors happen in the security filters, before the request reaches a controller, so
 * {@code GlobalExceptionHandler} never sees them. Only loaded with the web server: the planner import
 * runs without one.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
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
	 * Answers 401 when the token is missing, invalid, expired or its session has ended, and 503
	 * {@code SESSION_CHECK_UNAVAILABLE} when the only reason is that Keycloak could not be asked: the
	 * user should retry, not sign in again.
	 *
	 * @param request current request
	 * @param response response to write
	 * @param authException why authentication failed
	 * @throws IOException if the body cannot be written
	 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		if (onlySessionNotChecked(authException)) {
			write(request, response, ErrorCode.SESSION_CHECK_UNAVAILABLE);
			return;
		}
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		write(request, response, ErrorCode.UNAUTHORIZED);
	}

	/**
	 * Whether the token was refused only because Keycloak could not check its session. The validators
	 * all run, so an expired token can carry that error too; it must still answer 401.
	 *
	 * @param authException why authentication failed; the decoder's validation errors are in its causes
	 * @return {@code true} when {@code temporarily_unavailable} is the only validation error
	 */
	static boolean onlySessionNotChecked(AuthenticationException authException) {
		for (Throwable cause = authException; cause != null; cause = cause.getCause()) {
			if (cause instanceof JwtValidationException validation) {
				return !validation.getErrors().isEmpty() && validation.getErrors()
					.stream()
					.allMatch(error -> OAuth2ErrorCodes.TEMPORARILY_UNAVAILABLE.equals(error.getErrorCode()));
			}
		}
		return false;
	}

	/**
	 * Answers 403 when the user's role does not allow the operation.
	 * <p>
	 * A CSRF rejection answers 401 instead: requests with a bearer token skip the CSRF check, so only a
	 * write without token gets here, and the user has to sign in, not ask for another role.
	 *
	 * @param request current request
	 * @param response response to write
	 * @param accessDeniedException the denial
	 * @throws IOException if the body cannot be written
	 */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		if (accessDeniedException instanceof CsrfException) {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			write(request, response, ErrorCode.UNAUTHORIZED);
			return;
		}
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
