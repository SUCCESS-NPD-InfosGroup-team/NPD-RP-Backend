package com.npdinfosgroup.api.shared.exception;

import java.util.List;
import java.util.Map;

/**
 * One rejected field of a request.
 *
 * @param field path of the field in the request body, for example {@code assignment.leaderId}
 * @param code why it was rejected
 * @param messageArgs arguments of the message ({@code {0}}, {@code {1}}...)
 * @param details extra properties added to the error, for example {@code existingResourceId}
 */
public record FieldProblem(String field, FieldErrorCode code, List<Object> messageArgs, Map<String, Object> details) {

	/**
	 * Copies the lists so the problem cannot change after it is created.
	 *
	 * @param field path of the field
	 * @param code why it was rejected
	 * @param messageArgs arguments of the message
	 * @param details extra properties
	 */
	public FieldProblem {
		messageArgs = List.copyOf(messageArgs);
		details = Map.copyOf(details);
	}

	/**
	 * A problem without message arguments or extra properties.
	 *
	 * @param field path of the field
	 * @param code why it was rejected
	 * @return the problem
	 */
	public static FieldProblem of(String field, FieldErrorCode code) {
		return new FieldProblem(field, code, List.of(), Map.of());
	}

}
