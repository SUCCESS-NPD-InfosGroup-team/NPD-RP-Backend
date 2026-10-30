package com.npdinfosgroup.api.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes of the API, sent in the {@code code} property of every ProblemDetails response
 * so the frontends can switch on them without parsing texts.
 * <p>
 * Each code has its HTTP status and two message keys in {@code messages.properties}:
 * {@code error.<CODE>.title} and {@code error.<CODE>.detail}.
 */
public enum ErrorCode {

	/** One or more fields are invalid; the {@code errors} property lists them all. */
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

	/** The request is malformed (unreadable body, wrong parameter type...). */
	INVALID_REQUEST(HttpStatus.BAD_REQUEST),

	/** No token, or an invalid or expired one. */
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

	/** The user's role does not allow the operation. */
	FORBIDDEN(HttpStatus.FORBIDDEN),

	/** The resource, project or client does not exist. */
	NOT_FOUND(HttpStatus.NOT_FOUND),

	/** The employee number is already registered. */
	EMPLOYEE_NUMBER_TAKEN(HttpStatus.CONFLICT),

	/** The client already has a project with that name. */
	PROJECT_NAME_TAKEN(HttpStatus.CONFLICT),

	/** A client with that name already exists. */
	CLIENT_NAME_TAKEN(HttpStatus.CONFLICT),

	/** Someone else saved the record after the user read it; nothing was saved. */
	CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),

	/** The database could not be reached; nothing was saved and the request can be retried. */
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

	/** Unexpected failure. */
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	/**
	 * HTTP status answered with this code.
	 *
	 * @return the status
	 */
	public HttpStatus status() {
		return status;
	}

}
