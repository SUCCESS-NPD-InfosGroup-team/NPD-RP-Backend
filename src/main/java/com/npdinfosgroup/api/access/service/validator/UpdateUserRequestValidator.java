package com.npdinfosgroup.api.access.service.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.access.dto.request.UpdateUserRequest;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import com.npdinfosgroup.api.shared.security.Role;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * Checks the edit of a user before anything changes, and reports every invalid field at once: the
 * version that was read, the full name (required, up to 150, SCRUM-53 CA-12) and the role, one of the
 * four realm roles. Field messages share the scope of "Crear usuario".
 */
@Component
public class UpdateUserRequestValidator {

	private final Validator validator;

	/**
	 * Creates the validator.
	 *
	 * @param validator Jakarta Bean Validation
	 */
	UpdateUserRequestValidator(Validator validator) {
		this.validator = validator;
	}

	/**
	 * Validates the edit of a user.
	 *
	 * @param request the new values
	 * @return the chosen role
	 * @throws ValidationException with every invalid field, when there is at least one
	 */
	public Role validate(UpdateUserRequest request) {
		List<FieldProblem> problems = new ArrayList<>(validator.validate(request)
			.stream()
			.map(violation -> FieldProblem.of(violation.getPropertyPath().toString(),
					CreateUserRequestValidator.codeOf(violation)))
			.toList());
		Optional<Role> role = Role.ofRealmName(request.role());
		// A blank role is already REQUIRED; any other unknown code is reported as such
		if (role.isEmpty() && request.role() != null && !request.role().isBlank()) {
			problems.add(FieldProblem.of("role", FieldErrorCode.UNKNOWN_VALUE));
		}
		if (!problems.isEmpty()) {
			throw new ValidationException(CreateUserRequestValidator.MESSAGE_SCOPE, problems);
		}
		return role.orElseThrow();
	}

}
