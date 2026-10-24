package com.npdinfosgroup.api.planner.service.mapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.npdinfosgroup.api.planner.dto.response.AssignmentProjectResponse;
import com.npdinfosgroup.api.planner.dto.response.AssignmentResponse;
import com.npdinfosgroup.api.planner.dto.response.CodeLabel;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.ResourceResponse;
import com.npdinfosgroup.api.planner.dto.response.TerminationResponse;
import com.npdinfosgroup.api.planner.persistence.repository.ResourceRepository.Ref;
import com.npdinfosgroup.api.planner.persistence.repository.ResourceRepository.ResourceRow;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns a stored resource into the API response and into the values its audit log keeps.
 */
@Component
public class ResourceMapper {

	private final MessageSource messages;

	/**
	 * Creates the mapper.
	 *
	 * @param messages source of the situation labels and of "Indefinido"
	 */
	ResourceMapper(MessageSource messages) {
		this.messages = messages;
	}

	/**
	 * Builds the response of a resource.
	 * <p>
	 * {@code fieldsToFix} lists the fields that still hold an imported "No especificado" value, so the
	 * edit form can highlight them.
	 *
	 * @param row the stored resource
	 * @param updatedBy who changed it last through the API, if anyone
	 * @return the response
	 */
	public ResourceResponse toResponse(ResourceRow row, String updatedBy) {
		Locale locale = LocaleContextHolder.getLocale();
		List<String> fieldsToFix = new ArrayList<>();
		if (row.workModality() != null && !row.workModalityActive()) {
			fieldsToFix.add("workModeId");
		}
		if (row.project() != null && !row.projectActive()) {
			fieldsToFix.add("assignment.projectId");
		}
		return new ResourceResponse(row.id(), row.code(), row.fullName(), row.email(), idName(row.jobTitle()),
				idName(row.jobPosition()), row.level(), idName(row.businessUnit()), idName(row.workModality()),
				idName(row.gender()), idName(row.talentReview()), row.hireDate(), row.probationEndDate(),
				new CodeLabel(row.statusCode(), row.statusName()),
				new CodeLabel(row.situation(),
						messages.getMessage("resource.situation." + row.situation(), null, row.situation(), locale)),
				assignment(row), termination(row), List.copyOf(fieldsToFix), row.version(), row.updatedAt(),
				updatedBy);
	}

	/**
	 * Values of a resource as the audit log stores them: the names the user sees, by field.
	 * <p>
	 * Fields without a value are left out; "indefinido" is written as such so the log shows it was a
	 * choice and not a missing date.
	 *
	 * @param resource the resource
	 * @return the values, in form order
	 */
	public Map<String, String> auditValues(ResourceResponse resource) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("employeeNumber", resource.employeeNumber());
		values.put("name", resource.name());
		values.put("email", resource.email());
		values.put("jobTitle", name(resource.jobTitle()));
		values.put("position", name(resource.position()));
		values.put("level", Objects.toString(resource.level(), null));
		values.put("businessUnit", name(resource.businessUnit()));
		values.put("workMode", name(resource.workMode()));
		values.put("gender", name(resource.gender()));
		values.put("talentReview", name(resource.talentReview()));
		values.put("hireDate", text(resource.hireDate()));
		values.put("status", resource.status().label());
		AssignmentResponse assignment = resource.assignment();
		if (assignment != null) {
			values.put("assignment.client", name(assignment.client()));
			values.put("assignment.project", assignment.project().name());
			values.put("assignment.leader", name(assignment.leader()));
			values.put("assignment.clientRole", name(assignment.clientRole()));
			values.put("assignment.equipment", name(assignment.equipment()));
			values.put("assignment.endDate", assignment.indefinite()
					? messages.getMessage("resource.assignment.indefinite", null, LocaleContextHolder.getLocale())
					: text(assignment.endDate()));
			values.put("assignment.clientContactEmail", assignment.clientContactEmail());
		}
		TerminationResponse termination = resource.termination();
		if (termination != null) {
			values.put("termination.date", text(termination.date()));
			values.put("termination.reason", name(termination.reason()));
		}
		return values;
	}

	private static AssignmentResponse assignment(ResourceRow row) {
		if (row.project() == null) {
			return null;
		}
		return new AssignmentResponse(idName(row.client()),
				new AssignmentProjectResponse(row.project().id(), row.project().name(), row.contractEndDate()),
				idName(row.leader()), idName(row.clientRole()), idName(row.equipmentProvider()), row.projectEndDate(),
				row.projectEndDate() == null, row.clientContactEmail());
	}

	private static TerminationResponse termination(ResourceRow row) {
		if (row.terminationDate() == null && row.terminationReason() == null) {
			return null;
		}
		return new TerminationResponse(row.terminationDate(), idName(row.terminationReason()));
	}

	private static IdName idName(Ref ref) {
		return ref == null ? null : new IdName(ref.id(), ref.name());
	}

	private static String name(IdName value) {
		return value == null ? null : value.name();
	}

	private static String text(LocalDate date) {
		return date == null ? null : date.toString();
	}

}
