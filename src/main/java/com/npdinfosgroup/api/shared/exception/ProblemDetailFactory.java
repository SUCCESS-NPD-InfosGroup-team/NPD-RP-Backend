package com.npdinfosgroup.api.shared.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds the ProblemDetails (RFC 9457) bodies of the API with their texts from
 * {@code messages.properties}.
 * <p>
 * Shared by {@link GlobalExceptionHandler} and the security handlers, which answer 401 and 403 before
 * the request reaches a controller, so every error has the same shape: the standard fields plus
 * {@code code} and, for validation, {@code errors}.
 */
@Component
public class ProblemDetailFactory {

	private static final String TYPE_PREFIX = "https://npd-planner/errors/";

	private final MessageSource messages;

	/**
	 * Creates the factory.
	 *
	 * @param messages source of the user-facing texts
	 */
	ProblemDetailFactory(MessageSource messages) {
		this.messages = messages;
	}

	/**
	 * Builds the body of an error.
	 *
	 * @param code stable code; gives the status, the type and the message keys
	 * @param detailArgs arguments of the {@code error.<CODE>.detail} message
	 * @param locale language of the texts
	 * @return the body, with the {@code code} property set
	 */
	public ProblemDetail create(ErrorCode code, List<Object> detailArgs, Locale locale) {
		ProblemDetail problem = ProblemDetail.forStatus(code.status());
		problem.setType(URI.create(TYPE_PREFIX + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
		problem.setTitle(messages.getMessage("error." + code.name() + ".title", null, locale));
		problem.setDetail(messages.getMessage("error." + code.name() + ".detail", detailArgs.toArray(), locale));
		problem.setProperty("code", code.name());
		return problem;
	}

	/**
	 * Builds one entry of the {@code errors} list.
	 * <p>
	 * The message is looked up first for the specific field of the form
	 * ({@code validation.<scope>.<field>.<CODE>}), so a field can have its own wording, and then for the
	 * code alone ({@code validation.<CODE>}).
	 *
	 * @param scope form the field belongs to, for example {@code resource}
	 * @param problem the rejected field
	 * @param locale language of the message
	 * @return {@code field}, {@code code}, {@code message} and the problem's extra details
	 */
	public Map<String, Object> fieldError(String scope, FieldProblem problem, Locale locale) {
		Object[] args = problem.messageArgs().toArray();
		String code = problem.code().name();
		String message = messages.getMessage("validation." + scope + "." + problem.field() + "." + code, args, null,
				locale);
		if (message == null) {
			message = messages.getMessage("validation." + code, args, locale);
		}
		Map<String, Object> error = new LinkedHashMap<>();
		error.put("field", problem.field());
		error.put("code", code);
		error.put("message", message);
		error.putAll(problem.details());
		return error;
	}

}
