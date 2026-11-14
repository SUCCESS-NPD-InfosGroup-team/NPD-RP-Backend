package com.npdinfosgroup.api.access.service.validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.access.dto.request.CreateUserRequest;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import com.npdinfosgroup.api.shared.security.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

/**
 * Checks a new user before anything is created, and reports every invalid field at once: full name and
 * email required, the email well formed, and the role one of the four realm roles.
 */
@Component
public class CreateUserRequestValidator {

	/** Scope of the field messages of this form ({@code validation.user.<field>.<CODE>}). */
	static final String MESSAGE_SCOPE = "user";

	private final Validator validator;

	/**
	 * Creates the validator.
	 *
	 * @param validator Jakarta Bean Validation
	 */
	CreateUserRequestValidator(Validator validator) {
		this.validator = validator;
	}

	/**
	 * Validates a new user.
	 *
	 * @param request the user to create
	 * @return the chosen role
	 * @throws ValidationException with every invalid field, when there is at least one
	 */
	public Role validate(CreateUserRequest request) {
		List<FieldProblem> problems = new ArrayList<>(validator.validate(request)
			.stream()
			.map(violation -> FieldProblem.of(violation.getPropertyPath().toString(), codeOf(violation)))
			.toList());
		Optional<Role> role = Arrays.stream(Role.values()).filter(value -> value.realmName().equals(request.role())).findFirst();
		// A blank role is already REQUIRED; any other unknown code is reported as such
		if (role.isEmpty() && request.role() != null && !request.role().isBlank()) {
			problems.add(FieldProblem.of("role", FieldErrorCode.UNKNOWN_VALUE));
		}
		if (!problems.isEmpty()) {
			throw new ValidationException(MESSAGE_SCOPE, problems);
		}
		return role.orElseThrow();
	}

	private static FieldErrorCode codeOf(ConstraintViolation<?> violation) {
		Class<?> constraint = violation.getConstraintDescriptor().getAnnotation().annotationType();
		if (constraint == NotBlank.class) {
			return FieldErrorCode.REQUIRED;
		}
		return constraint == Size.class ? FieldErrorCode.TOO_LONG : FieldErrorCode.INVALID_FORMAT;
	}

}
