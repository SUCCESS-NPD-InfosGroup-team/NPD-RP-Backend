package com.npdinfosgroup.api.shared.exception;

import java.util.List;
import java.util.Map;

/**
 * A business error that {@link GlobalExceptionHandler} turns into a ProblemDetails response.
 * <p>
 * Services throw it (or a subclass) with a stable {@link ErrorCode}; the texts come from
 * {@code messages.properties}, so the code never carries user-facing words.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final List<Object> detailArgs;

	private final transient Map<String, Object> properties;

	/**
	 * Creates the error.
	 *
	 * @param code stable code, which also gives the HTTP status
	 * @param detailArgs arguments of the {@code error.<CODE>.detail} message
	 * @param properties extra properties of the response, such as {@code existingResourceId}
	 */
	public ApiException(ErrorCode code, List<Object> detailArgs, Map<String, Object> properties) {
		super(code.name());
		this.code = code;
		this.detailArgs = List.copyOf(detailArgs);
		this.properties = Map.copyOf(properties);
	}

	/**
	 * Stable code of the error.
	 *
	 * @return the code
	 */
	public ErrorCode getCode() {
		return code;
	}

	/**
	 * Arguments of the detail message.
	 *
	 * @return the arguments, in order
	 */
	public List<Object> getDetailArgs() {
		return detailArgs;
	}

	/**
	 * Extra properties added to the response.
	 *
	 * @return the properties by name
	 */
	public Map<String, Object> getProperties() {
		return properties;
	}

}
