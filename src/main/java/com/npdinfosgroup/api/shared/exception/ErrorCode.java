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

	/** The project is inactive and cannot be edited until it is reactivated. */
	PROJECT_INACTIVE(HttpStatus.CONFLICT),

	/** The project still has people and cannot be deactivated. */
	PROJECT_HAS_TEAM(HttpStatus.CONFLICT),

	/** A client with that name already exists. */
	CLIENT_NAME_TAKEN(HttpStatus.CONFLICT),

	/** A user with that email already exists. */
	EMAIL_TAKEN(HttpStatus.CONFLICT),

	/** Someone else saved the record after the user read it; nothing was saved. */
	CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),

	/** The change would leave no active Operations administrator; nothing was changed. */
	LAST_OPERATIONS_ADMINISTRATOR(HttpStatus.CONFLICT),

	/** The user is an NPD lead without an employee record, a data error: the account must be deleted. */
	NPD_LEAD_WITHOUT_EMPLOYEE(HttpStatus.CONFLICT),

	/** The account is deactivated: it must be reactivated before its name or role can change. */
	ACCOUNT_DISABLED(HttpStatus.CONFLICT),

	/** An NPD lead employee has the email or number of the new NPD lead: confirm it is the same person. */
	EXISTING_NPD_LEAD_EMPLOYEE(HttpStatus.CONFLICT),

	/**
	 * The employee record cannot be linked to the user: it has another user, is not an NPD lead, or is on
	 * Baja for another reason; the {@code reason} property says which.
	 */
	EMPLOYEE_NOT_LINKABLE(HttpStatus.CONFLICT),

	/** The deactivated account reached its deletion time and can no longer be reactivated. */
	ACCOUNT_DELETION_DUE(HttpStatus.CONFLICT),

	/** The database could not be reached; nothing was saved and the request can be retried. */
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

	/** Keycloak could not be reached; nothing was created and the request can be retried. */
	IDENTITY_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

	/** Keycloak could not check whether the session is still active; the request can be retried. */
	SESSION_CHECK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

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
