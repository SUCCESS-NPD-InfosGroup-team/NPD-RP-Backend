package com.npdinfosgroup.api.shared.exception;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error of the API into a ProblemDetails (RFC 9457) response with a stable {@code code}.
 * <p>
 * Extends {@link ResponseEntityExceptionHandler} so Spring MVC errors (unreadable body, unknown
 * route...) also come out as ProblemDetails.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ProblemDetailFactory problems;

	/**
	 * Creates the handler.
	 *
	 * @param problems builder of the response bodies
	 */
	GlobalExceptionHandler(ProblemDetailFactory problems) {
		this.problems = problems;
	}

	/**
	 * Business errors thrown by the services: validation (with every rejected field in
	 * {@code errors}), duplicates and missing records.
	 *
	 * @param ex the error
	 * @param locale language of the request
	 * @return the response with the error's status
	 */
	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex, Locale locale) {
		ProblemDetail problem = problems.create(ex.getCode(), ex.getDetailArgs(), locale);
		ex.getProperties().forEach(problem::setProperty);
		if (ex instanceof ValidationException validation) {
			problem.setProperty("errors",
					validation.getProblems().stream().map(field -> problems.fieldError(validation.getScope(), field, locale)).toList());
		}
		return ResponseEntity.status(ex.getCode().status()).body(problem);
	}

	/**
	 * The database is down or did not answer. The transaction was rolled back, so nothing was saved
	 * and the user can retry.
	 *
	 * @param ex the error; only its type is logged because database messages can carry personal data
	 * @param locale language of the request
	 * @return a 503 response
	 */
	@ExceptionHandler({ DataAccessResourceFailureException.class, TransientDataAccessException.class,
			CannotCreateTransactionException.class })
	ResponseEntity<ProblemDetail> handleDatabaseUnavailable(Exception ex, Locale locale) {
		log.error("Database unavailable: {}", ex.getClass().getName());
		return respond(ErrorCode.SERVICE_UNAVAILABLE, locale);
	}

	/**
	 * Any other failure, answered without internal details.
	 *
	 * @param ex the error
	 * @param locale language of the request
	 * @return a 500 response
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, Locale locale) {
		log.error("Unexpected error", ex);
		return respond(ErrorCode.INTERNAL_ERROR, locale);
	}

	/**
	 * Adds the {@code code} property to the ProblemDetails that Spring MVC builds for its own errors.
	 *
	 * @param ex the error
	 * @param body the body Spring built
	 * @param headers response headers
	 * @param statusCode response status
	 * @param request current request
	 * @return the response
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail problem) {
			problem.setProperty("code", codeFor(statusCode).name());
		}
		return super.handleExceptionInternal(ex, body, headers, statusCode, request);
	}

	private static ErrorCode codeFor(HttpStatusCode statusCode) {
		if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
			return ErrorCode.NOT_FOUND;
		}
		return statusCode.is4xxClientError() ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_ERROR;
	}

	private ResponseEntity<ProblemDetail> respond(ErrorCode code, Locale locale) {
		return ResponseEntity.status(code.status()).body(problems.create(code, List.of(), locale));
	}

}
