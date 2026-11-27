package com.npdinfosgroup.api.access.service.validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.npdinfosgroup.api.access.dto.request.CreateUserRequest;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import com.npdinfosgroup.api.shared.security.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

/**
 * Checks a new user before anything is created, and reports every invalid field at once: full name and
 * email required, the email well formed, and the role one of the {@link #ASSIGNABLE} roles.
 * <p>
 * <b>For now</b> NPD leads cannot be created here (SCRUM-241). An NPD lead must also have an employee
 * record marked as NPD lead, or they could never be chosen as the lead of a collaborator; this form
 * only creates the platform user. The block stays until the NPD lead registration (SCRUM-239, HU-10)
 * exists, which creates both and decides how this form offers the role again.
 */
@Component
public class CreateUserRequestValidator {

	/** Scope of the field messages of this form ({@code validation.user.<field>.<CODE>}). */
	public static final String MESSAGE_SCOPE = "user";

	/**
	 * Roles that "Crear usuario" can give, in the order of {@link Role}: every realm role but NPD lead,
	 * left out until SCRUM-239 (HU-10) registers NPD leads together with their employee record.
	 */
	public static final Set<Role> ASSIGNABLE = Collections
		.unmodifiableSet(EnumSet.of(Role.OPERATIONS_ADMINISTRATOR, Role.CUSTOMER_SUCCESS, Role.GUEST));

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
		Optional<Role> role = Role.ofRealmName(request.role());
		// A blank role is already REQUIRED; any other unknown code is reported as such
		if (role.isEmpty() && request.role() != null && !request.role().isBlank()) {
			problems.add(FieldProblem.of("role", FieldErrorCode.UNKNOWN_VALUE));
		}
		else if (role.isPresent() && !ASSIGNABLE.contains(role.get())) {
			problems.add(FieldProblem.of("role", FieldErrorCode.ROLE_NOT_ASSIGNABLE));
		}
		if (!problems.isEmpty()) {
			throw new ValidationException(MESSAGE_SCOPE, problems);
		}
		return role.orElseThrow();
	}

	/**
	 * The field error code of a Jakarta violation.
	 *
	 * @param violation the violation
	 * @return {@code REQUIRED} for a missing value, {@code TOO_LONG} for a size, {@code INVALID_FORMAT} otherwise
	 */
	static FieldErrorCode codeOf(ConstraintViolation<?> violation) {
		Class<?> constraint = violation.getConstraintDescriptor().getAnnotation().annotationType();
		if (constraint == NotBlank.class || constraint == NotNull.class) {
			return FieldErrorCode.REQUIRED;
		}
		return constraint == Size.class ? FieldErrorCode.TOO_LONG : FieldErrorCode.INVALID_FORMAT;
	}

}
