package com.npdinfosgroup.api.shared.exception;

import java.util.List;
import java.util.Map;

/**
 * The requested record does not exist (404).
 */
public class NotFoundException extends ApiException {

	/**
	 * Creates the error.
	 *
	 * @param id identifier that was not found, shown in the detail message
	 */
	public NotFoundException(Object id) {
		// As text, so the message does not format it as a number ("999.999")
		super(ErrorCode.NOT_FOUND, List.of(String.valueOf(id)), Map.of());
	}

}
