package com.npdinfosgroup.api.access.client;

import java.util.List;
import java.util.Map;

import com.npdinfosgroup.api.shared.exception.ApiException;
import com.npdinfosgroup.api.shared.exception.ErrorCode;

/**
 * Keycloak did not answer, timed out or failed with a 5xx (503 {@code IDENTITY_PROVIDER_UNAVAILABLE}).
 * The request can be retried.
 */
public class IdentityProviderUnavailableException extends ApiException {

	/**
	 * Creates the error.
	 *
	 * @param cause the failure of the HTTP call
	 */
	public IdentityProviderUnavailableException(Throwable cause) {
		super(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE, List.of(), Map.of());
		initCause(cause);
	}

}
