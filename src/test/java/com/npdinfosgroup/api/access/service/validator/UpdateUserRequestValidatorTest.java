package com.npdinfosgroup.api.access.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.npdinfosgroup.api.access.dto.request.UpdateUserRequest;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import com.npdinfosgroup.api.shared.security.Role;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The fields of a user edit: every error reported at once, in the scope of the user form.
 */
class UpdateUserRequestValidatorTest {

	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

	private final UpdateUserRequestValidator validator = new UpdateUserRequestValidator(FACTORY.getValidator());

	@AfterAll
	static void close() {
		FACTORY.close();
	}

	/** Any of the four roles is accepted, NPD lead included. */
	@ParameterizedTest
	@ValueSource(strings = { "operations-administrator", "npd-leader", "customer-success", "guest" })
	void acceptsEveryRole(String role) {
		assertThat(validator.validate(new UpdateUserRequest(0, "Ana Ríos", role, null)).realmName()).isEqualTo(role);
	}

	/** CA-12: a blank name, a missing version and a missing role come together. */
	@Test
	void reportsEveryMissingField() {
		assertThatThrownBy(() -> validator.validate(new UpdateUserRequest(null, "   ", "", null)))
			.isInstanceOfSatisfying(ValidationException.class, ex -> {
				assertThat(ex.getScope()).isEqualTo("user");
				assertThat(ex.getProblems()).extracting(FieldProblem::field, FieldProblem::code)
					.containsExactlyInAnyOrder(tuple("version", FieldErrorCode.REQUIRED),
							tuple("fullName", FieldErrorCode.REQUIRED),
							tuple("role", FieldErrorCode.REQUIRED));
			});
	}

	/** A name over 150 characters and an unknown role. */
	@Test
	void reportsALongNameAndAnUnknownRole() {
		assertThatThrownBy(() -> validator.validate(new UpdateUserRequest(0, "a".repeat(151), "boss", null)))
			.isInstanceOfSatisfying(ValidationException.class,
					ex -> assertThat(ex.getProblems()).extracting(FieldProblem::field, FieldProblem::code)
						.containsExactlyInAnyOrder(tuple("fullName", FieldErrorCode.TOO_LONG),
								tuple("role", FieldErrorCode.UNKNOWN_VALUE)));
	}

	/** The role of a valid request is returned. */
	@Test
	void returnsTheRole() {
		assertThat(validator.validate(new UpdateUserRequest(3, "Ana", "guest", null))).isEqualTo(Role.GUEST);
	}

}
