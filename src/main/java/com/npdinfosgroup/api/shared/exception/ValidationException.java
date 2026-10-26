package com.npdinfosgroup.api.shared.exception;

import java.util.List;
import java.util.Map;

/**
 * Every invalid field of a request at once, so the form can list them in a single summary.
 */
public class ValidationException extends ApiException {

	private final String scope;

	private final transient List<FieldProblem> problems;

	/**
	 * Creates the error.
	 *
	 * @param scope form the fields belong to, for example {@code resource} or {@code project}; picks the
	 *        field messages, because two forms can have a field with the same name
	 * @param problems the rejected fields; at least one
	 */
	public ValidationException(String scope, List<FieldProblem> problems) {
		super(ErrorCode.VALIDATION_FAILED, List.of(problems.size()), Map.of());
		this.scope = scope;
		this.problems = List.copyOf(problems);
	}

	/**
	 * Form the fields belong to.
	 *
	 * @return the scope of the field messages
	 */
	public String getScope() {
		return scope;
	}

	/**
	 * The rejected fields.
	 *
	 * @return the problems, in the order they were found
	 */
	public List<FieldProblem> getProblems() {
		return problems;
	}

}
