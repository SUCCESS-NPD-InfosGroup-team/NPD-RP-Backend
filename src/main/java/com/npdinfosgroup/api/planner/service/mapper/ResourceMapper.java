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
import com.npdinfosgroup.api.planner.persistence.entity.CatalogEntity;
import com.npdinfosgroup.api.planner.persistence.entity.Employee;
import com.npdinfosgroup.api.planner.persistence.entity.Project;
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
	 * @param messages source of the situation labels, of "Indefinido" and of "Sí"/"No"
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
	 * Reads the related catalogs of the employee, so it must run inside the transaction that loaded it.
	 *
	 * @param employee the stored resource
	 * @param situation its situation, calculated by {@code resource_v}
	 * @param updatedBy who changed it last through the API, if anyone
	 * @return the response
	 */
	public ResourceResponse toResponse(Employee employee, String situation, String updatedBy) {
		Locale locale = LocaleContextHolder.getLocale();
		List<String> fieldsToFix = new ArrayList<>();
		if (employee.getWorkModality() != null && !employee.getWorkModality().isActive()) {
			fieldsToFix.add("workModeId");
		}
		if (employee.getProject() != null && !employee.getProject().isActive()) {
			fieldsToFix.add("assignment.projectId");
		}
		return new ResourceResponse(employee.getId(), employee.getCode(), employee.getFullName(), employee.getEmail(),
				idName(employee.getJobTitle()), idName(employee.getJobPosition()), employee.getLevel(),
				idName(employee.getBusinessUnit()), idName(employee.getWorkModality()), idName(employee.getGender()),
				idName(employee.getTalentReview()), employee.getHireDate(), employee.isOnProbation(),
				employee.getProbationEndDate(),
				new CodeLabel(employee.getStatus().getCode(), employee.getStatus().getName()),
				new CodeLabel(situation, messages.getMessage("resource.situation." + situation, null, situation, locale)),
				assignment(employee), termination(employee), List.copyOf(fieldsToFix), employee.getVersion(),
				employee.getUpdatedAt(), updatedBy);
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
		values.put("onProbation", messages.getMessage(resource.onProbation() ? "audit.value.yes" : "audit.value.no",
				null, LocaleContextHolder.getLocale()));
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

	/**
	 * Values of a stored resource as the audit log stores them, to compare a record before and after an
	 * edit. The situation is not audited: the database calculates it.
	 * <p>
	 * Reads the related catalogs of the employee, so it must run inside the transaction that loaded it.
	 *
	 * @param employee the stored resource
	 * @return the values, in form order
	 */
	public Map<String, String> auditValues(Employee employee) {
		return auditValues(toResponse(employee, null, null));
	}

	private static AssignmentResponse assignment(Employee employee) {
		Project project = employee.getProject();
		if (project == null) {
			return null;
		}
		Employee leader = employee.getLeader();
		return new AssignmentResponse(new IdName(project.getClient().getId(), project.getClient().getName()),
				new AssignmentProjectResponse(project.getId(), project.getName(), project.getContractEndDate()),
				leader == null ? null : new IdName(leader.getId(), leader.getFullName()),
				idName(employee.getClientRole()), idName(employee.getEquipmentProvider()),
				employee.getProjectEndDate(), employee.getProjectEndDate() == null,
				employee.getClientContactEmail());
	}

	private static TerminationResponse termination(Employee employee) {
		if (employee.getTerminationDate() == null && employee.getTerminationReason() == null) {
			return null;
		}
		return new TerminationResponse(employee.getTerminationDate(), idName(employee.getTerminationReason()));
	}

	private static IdName idName(CatalogEntity value) {
		return value == null ? null : new IdName(value.getId().longValue(), value.getName());
	}

	private static String name(IdName value) {
		return value == null ? null : value.name();
	}

	private static String text(LocalDate date) {
		return date == null ? null : date.toString();
	}

}
