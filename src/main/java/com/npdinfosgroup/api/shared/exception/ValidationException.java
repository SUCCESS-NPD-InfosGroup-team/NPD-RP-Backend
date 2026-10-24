package com.npdinfosgroup.api.shared.exception;

import java.util.List;
import java.util.Map;

/**
 * Every invalid field of a request at once, so the form can list them in a single summary.
 */
public class ValidationException extends ApiException {

	private final transient List<FieldProblem> problems;

	/**
	 * Creates the error.
	 *
	 * @param problems the rejected fields; at least one
	 */
	public ValidationException(List<FieldProblem> problems) {
		super(ErrorCode.VALIDATION_FAILED, List.of(problems.size()), Map.of());
		this.problems = List.copyOf(problems);
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
