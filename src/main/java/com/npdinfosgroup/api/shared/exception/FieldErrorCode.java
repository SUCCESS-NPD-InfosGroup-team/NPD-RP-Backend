package com.npdinfosgroup.api.shared.exception;

/**
 * Why a single field was rejected, sent as {@code errors[].code} in a {@link ErrorCode#VALIDATION_FAILED}
 * response.
 * <p>
 * The message shown to the user is {@code validation.<field>.<CODE>} when that key exists (to word it
 * for a specific field) and {@code validation.<CODE>} otherwise.
 */
public enum FieldErrorCode {

	/** The field is required and is missing or blank. */
	REQUIRED,

	/** The value does not have the expected format (e-mail, positive number...). */
	INVALID_FORMAT,

	/** The text is longer than allowed. */
	TOO_LONG,

	/** The value is not one of the allowed codes. */
	UNKNOWN_VALUE,

	/** The id does not exist in its catalog or table. */
	UNKNOWN_ID,

	/** The id is an inactive "No especificado" (or otherwise deactivated) catalog value. */
	NOT_SPECIFIED_VALUE,

	/** The project is inactive and cannot receive people. */
	INACTIVE_PROJECT,

	/** The client is inactive and cannot receive new projects. */
	INACTIVE_CLIENT,

	/** The chosen NPD lead is on Baja. */
	INACTIVE_LEADER,

	/** The chosen NPD lead is the edited resource itself. */
	SELF_LEADER,

	/** The date has already passed. */
	DATE_IN_PAST,

	/** The date is earlier than the contract start date. */
	DATE_BEFORE_HIRE,

	/** Two fields that exclude each other were both given (end date and "indefinido"). */
	CONFLICTING_VALUES,

	/** The e-mail already belongs to another employee. */
	EMAIL_TAKEN,

	/** The employee number already belongs to another employee. */
	EMPLOYEE_NUMBER_TAKEN

}
