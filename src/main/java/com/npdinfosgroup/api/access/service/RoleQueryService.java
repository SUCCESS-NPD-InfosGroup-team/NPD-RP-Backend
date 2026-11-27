package com.npdinfosgroup.api.access.service;

import java.util.Arrays;
import java.util.Locale;

import com.npdinfosgroup.api.access.dto.response.RoleResponse;
import com.npdinfosgroup.api.access.dto.response.RolesResponse;
import com.npdinfosgroup.api.access.service.validator.CreateUserRequestValidator;
import com.npdinfosgroup.api.shared.security.Role;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/**
 * The roles a user can get when created from "Crear usuario", with the name and description the
 * frontend shows on the role cards (CA-10). For now NPD lead is left out (SCRUM-241), so nobody creates
 * an NPD lead without an employee record; it comes back with the NPD lead registration (SCRUM-239). The
 * texts come from {@code messages.properties}: {@code role.<code>.name} and
 * {@code role.<code>.description}.
 */
@Service
public class RoleQueryService {

	private final MessageSource messages;

	/**
	 * Creates the service.
	 *
	 * @param messages texts of the roles
	 */
	RoleQueryService(MessageSource messages) {
		this.messages = messages;
	}

	/**
	 * The roles that can be given when creating a user, in the order of {@link Role}.
	 *
	 * @param locale language of the texts
	 * @return the roles
	 */
	public RolesResponse list(Locale locale) {
		return new RolesResponse(Arrays.stream(Role.values())
			.filter(CreateUserRequestValidator.ASSIGNABLE::contains)
			.map(role -> new RoleResponse(role.realmName(), text(role, "name", locale), text(role, "description", locale)))
			.toList());
	}

	private String text(Role role, String part, Locale locale) {
		return messages.getMessage("role." + role.realmName() + "." + part, null, locale);
	}

}
