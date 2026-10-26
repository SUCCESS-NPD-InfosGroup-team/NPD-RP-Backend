package com.npdinfosgroup.api.planner.service.validator;

import java.util.List;
import java.util.Set;

import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Turns the Jakarta violations of a request into the field problems of the API, so the form validators
 * report them together with their own rules.
 */
final class ConstraintProblems {

	private ConstraintProblems() {
	}

	/**
	 * The problems of some violations.
	 *
	 * @param violations Jakarta violations of a request or of one of its parts
	 * @param prefix path of that part in the request, for example {@code assignment.}; empty for the
	 *        request itself
	 * @return one problem per violation
	 */
	static <T> List<FieldProblem> of(Set<ConstraintViolation<T>> violations, String prefix) {
		return violations.stream()
			.map(violation -> FieldProblem.of(prefix + violation.getPropertyPath(), codeOf(violation)))
			.toList();
	}

	private static FieldErrorCode codeOf(ConstraintViolation<?> violation) {
		Class<?> constraint = violation.getConstraintDescriptor().getAnnotation().annotationType();
		if (constraint == NotNull.class || constraint == NotBlank.class) {
			return FieldErrorCode.REQUIRED;
		}
		return constraint == Size.class ? FieldErrorCode.TOO_LONG : FieldErrorCode.INVALID_FORMAT;
	}

}
