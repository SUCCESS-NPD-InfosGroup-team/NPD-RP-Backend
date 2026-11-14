package com.npdinfosgroup.api.history.service.mapper;

import java.util.List;
import java.util.Locale;

import com.npdinfosgroup.api.history.dto.response.AuditFieldOption;
import com.npdinfosgroup.api.history.dto.response.AuditLogEntryResponse;
import com.npdinfosgroup.api.history.dto.response.FieldChangeResponse;
import com.npdinfosgroup.api.history.dto.response.LastChangeResponse;
import com.npdinfosgroup.api.history.persistence.entity.AuditEntry;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns audit rows into the API response, with the label the user sees for each field.
 */
@Component
public class AuditMapper {

	private static final String LABEL_PREFIX = "audit.field.";

	private final MessageSource messages;

	/**
	 * Creates the mapper.
	 *
	 * @param messages source of the field labels ({@code audit.field.<field>})
	 */
	AuditMapper(MessageSource messages) {
		this.messages = messages;
	}

	/**
	 * Builds the response of one operation. A field without a label keeps its technical name, so a field
	 * audited in the future never breaks the answer.
	 *
	 * @param rows the rows of the operation, in the order they were written; at least one
	 * @return the operation with all its fields
	 */
	public LastChangeResponse toLastChange(List<AuditEntry> rows) {
		AuditEntry first = rows.getFirst();
		return new LastChangeResponse(first.getAction(), first.getActor(), first.getOccurredAt(), fields(rows));
	}

	/**
	 * Builds one entry of the audit log: an edited field, or a whole registration.
	 *
	 * @param rows the edited field's row, or every row of the registration in the order they were written;
	 *        at least one
	 * @return the entry, identified by its first row
	 */
	public AuditLogEntryResponse toLogEntry(List<AuditEntry> rows) {
		AuditEntry first = rows.getFirst();
		return new AuditLogEntryResponse(first.getId(), first.getAction(), first.getActor(), first.getOccurredAt(),
				fields(rows));
	}

	/**
	 * Builds an option of the "Campo" filter.
	 *
	 * @param field technical name of the field
	 * @return the field with the label the user sees
	 */
	public AuditFieldOption toFieldOption(String field) {
		return new AuditFieldOption(field, label(field, LocaleContextHolder.getLocale()));
	}

	private List<FieldChangeResponse> fields(List<AuditEntry> rows) {
		Locale locale = LocaleContextHolder.getLocale();
		return rows.stream()
			.map(row -> new FieldChangeResponse(row.getField(), label(row.getField(), locale), row.getOldValue(),
					row.getNewValue()))
			.toList();
	}

	private String label(String field, Locale locale) {
		return messages.getMessage(LABEL_PREFIX + field, null, field, locale);
	}

}
