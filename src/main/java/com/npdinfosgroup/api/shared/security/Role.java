package com.npdinfosgroup.api.shared.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Realm roles defined in Keycloak. The access token lists them in {@code realm_access.roles}, and
 * {@link SecurityConfig} turns each one into the authority {@code ROLE_<name>}.
 */
public enum Role {

	/** Operations administrator: creates and edits resources and projects. */
	OPERATIONS_ADMINISTRATOR("operations-administrator"),

	/** NPD lead: read-only for now. */
	NPD_LEADER("npd-leader"),

	/** Sales / Customer Success: read-only for now. */
	CUSTOMER_SUCCESS("customer-success"),

	/** Guest: read-only. */
	GUEST("guest");

	private final String realmName;

	Role(String realmName) {
		this.realmName = realmName;
	}

	/**
	 * Name of the role in the Keycloak realm, as it comes in the token.
	 *
	 * @return the realm role name
	 */
	public String realmName() {
		return realmName;
	}

	/**
	 * The role with a realm name.
	 *
	 * @param realmName name of the role in the realm, as the token and the API send it
	 * @return the role, or empty if there is none with that name
	 */
	public static Optional<Role> ofRealmName(String realmName) {
		return Arrays.stream(values()).filter(role -> role.realmName.equals(realmName)).findFirst();
	}

}
