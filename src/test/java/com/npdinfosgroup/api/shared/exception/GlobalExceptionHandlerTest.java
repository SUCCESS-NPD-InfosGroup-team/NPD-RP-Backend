package com.npdinfosgroup.api.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * The responses of {@link GlobalExceptionHandler} that the API tests cannot trigger: a database that
 * does not answer, an unexpected failure and the errors Spring MVC builds itself.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ProblemDetailFactory(messages()));

	/** A database that does not answer is a 503 the user can retry. */
	@Test
	void answersServiceUnavailableWhenTheDatabaseIsDown() {
		ResponseEntity<ProblemDetail> response = handler
			.handleDatabaseUnavailable(new DataAccessResourceFailureException("down"), Locale.ROOT);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody().getProperties()).containsEntry("code", "SERVICE_UNAVAILABLE");
		assertThat(response.getBody().getDetail()).isEqualTo("error.SERVICE_UNAVAILABLE.detail");
	}

	/** Anything else is a 500 without internal details. */
	@Test
	void answersInternalErrorOnUnexpectedFailures() {
		ResponseEntity<ProblemDetail> response = handler.handleUnexpected(new IllegalStateException("boom"),
				Locale.ROOT);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().getProperties()).containsEntry("code", "INTERNAL_ERROR");
		assertThat(response.getBody().getDetail()).doesNotContain("boom");
	}

	/** Business errors keep their extra properties and get a type from their code. */
	@Test
	void keepsTheExtraPropertiesOfBusinessErrors() {
		ResponseEntity<ProblemDetail> response = handler.handleApiException(
				new ApiException(ErrorCode.EMPLOYEE_NUMBER_TAKEN, List.of("1045", ""), Map.of("existingResourceId", 7L)),
				Locale.ROOT);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().getProperties()).containsEntry("existingResourceId", 7L)
			.containsEntry("code", "EMPLOYEE_NUMBER_TAKEN");
		assertThat(response.getBody().getType()).hasToString("https://npd-planner/errors/employee-number-taken");
	}

	/** Spring MVC errors get a code by status: 404, other client errors and server errors. */
	@Test
	void addsCodeToSpringErrors() {
		ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

		assertThat(codeOf(handleInternal(HttpStatus.NOT_FOUND, request))).isEqualTo("NOT_FOUND");
		assertThat(codeOf(handleInternal(HttpStatus.METHOD_NOT_ALLOWED, request))).isEqualTo("INVALID_REQUEST");
		assertThat(codeOf(handleInternal(HttpStatus.INTERNAL_SERVER_ERROR, request))).isEqualTo("INTERNAL_ERROR");
	}

	private ResponseEntity<Object> handleInternal(HttpStatus status, ServletWebRequest request) {
		return handler.handleExceptionInternal(new IllegalStateException(), ProblemDetail.forStatus(status),
				new HttpHeaders(), status, request);
	}

	private static Object codeOf(ResponseEntity<Object> response) {
		return ((ProblemDetail) response.getBody()).getProperties().get("code");
	}

	private static StaticMessageSource messages() {
		StaticMessageSource messages = new StaticMessageSource();
		messages.setUseCodeAsDefaultMessage(true);
		return messages;
	}

}
