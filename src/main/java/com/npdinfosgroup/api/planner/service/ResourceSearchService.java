package com.npdinfosgroup.api.planner.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.npdinfosgroup.api.planner.dto.request.ResourceSearchCriteria;
import com.npdinfosgroup.api.planner.dto.response.CodeLabel;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.PageInfo;
import com.npdinfosgroup.api.planner.dto.response.ResourceGroupResponse;
import com.npdinfosgroup.api.planner.dto.response.ResourceListItemResponse;
import com.npdinfosgroup.api.planner.dto.response.ResourceSearchResponse;
import com.npdinfosgroup.api.planner.persistence.entity.RosterEntry;
import com.npdinfosgroup.api.planner.persistence.query.RosterEntrySpecifications;
import com.npdinfosgroup.api.planner.persistence.repository.RosterEntryRepository;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Searches the roster (HU-03): one row per collaborator, narrowed by every filter at once and grouped by
 * project.
 * <p>
 * The groups go in a fixed order: first the people on an inactive "No especificado" project, who must be
 * corrected, then the people without project, then the projects whose contract with the client ends
 * soonest. Inside a group, whoever leaves the project first goes first.
 */
@Service
public class ResourceSearchService {

	/** Situations calculated by {@code resource_v}. */
	static final Set<String> SITUATIONS = Set.of("ASIGNADO", "PENDIENTE_CONFIRMACION", "SIN_PROYECTO");

	private static final String GROUP_NOT_SPECIFIED = "NOT_SPECIFIED";

	private static final String GROUP_NO_PROJECT = "NO_PROJECT";

	private static final String GROUP_PROJECT = "PROJECT";

	/** Scope of the field messages of the search ({@code validation.resourceSearch.<field>.<CODE>}). */
	private static final String MESSAGE_SCOPE = "resourceSearch";

	private static final Sort ROSTER_ORDER = Sort.by(Order.asc("rosterGroup"), Order.asc("contractEndDate").nullsLast(),
			Order.asc("clientName"), Order.asc("projectName"), Order.asc("projectId"),
			Order.asc("projectEndDate").nullsLast(), Order.asc("fullName"), Order.asc("id"));

	private final RosterEntryRepository roster;

	private final CatalogService catalogs;

	private final MessageSource messages;

	private final Clock clock;

	/**
	 * Creates the service.
	 *
	 * @param roster rows of the roster
	 * @param catalogs checks the status codes
	 * @param messages labels of the situations
	 * @param clock gives today for the days to the end date
	 */
	ResourceSearchService(RosterEntryRepository roster, CatalogService catalogs, MessageSource messages,
			Clock clock) {
		this.roster = roster;
		this.catalogs = catalogs;
		this.messages = messages;
		this.clock = clock;
	}

	/**
	 * A page of the roster that matches every filter, with the groups of its rows.
	 * <p>
	 * The text is searched in the name, the employee number, the project and the client, ignoring case
	 * and accents. Several statuses or situations match any of them: the "Activo" filter of the screen
	 * sends {@code ACTIVO} and {@code LICENCIA}.
	 *
	 * @param criteria the filters; {@code null} or empty ones are not applied
	 * @param pageable page number and size; its sort is ignored, the order is always the same
	 * @return the rows, the groups they belong to with their totals, and the page
	 * @throws ValidationException if a status or situation is unknown
	 */
	@Transactional(readOnly = true)
	public ResourceSearchResponse search(ResourceSearchCriteria criteria, Pageable pageable) {
		Specification<RosterEntry> filters = filters(criteria);
		Page<RosterEntry> page = roster.findAll(filters,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ROSTER_ORDER));
		LocalDate today = LocalDate.now(clock);
		List<ResourceListItemResponse> items = page.getContent().stream().map(row -> item(row, today)).toList();
		return new ResourceSearchResponse(items, groups(page.getContent(), filters),
				new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
	}

	/**
	 * Lower case without accents or repeated spaces, like the {@code search_text} column of
	 * {@code resource_v}.
	 *
	 * @param text the text typed by the user
	 * @return the normalized text
	 */
	static String normalize(String text) {
		String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
	}

	private Specification<RosterEntry> filters(ResourceSearchCriteria criteria) {
		checkValues(criteria);
		List<Specification<RosterEntry>> filters = new ArrayList<>();
		if (criteria.text() != null && !criteria.text().isBlank()) {
			filters.add(RosterEntrySpecifications.containing(normalize(criteria.text())));
		}
		addEqual(filters, "clientId", criteria.clientId());
		addEqual(filters, "projectId", criteria.projectId());
		addEqual(filters, "leaderId", criteria.leaderId());
		addEqual(filters, "clientRoleId", criteria.clientRoleId());
		addEqual(filters, "level", criteria.level());
		if (hasValues(criteria.statuses())) {
			filters.add(RosterEntrySpecifications.in("statusCode", upperCase(criteria.statuses())));
		}
		if (hasValues(criteria.situations())) {
			filters.add(RosterEntrySpecifications.in("situation", upperCase(criteria.situations())));
		}
		return Specification.allOf(filters);
	}

	/** Unknown statuses or situations are an error, not an empty list, so a typo is not silent. */
	private void checkValues(ResourceSearchCriteria criteria) {
		List<FieldProblem> problems = new ArrayList<>();
		if (hasValues(criteria.statuses())
				&& upperCase(criteria.statuses()).stream().anyMatch(code -> catalogs.findStatusId(code).isEmpty())) {
			problems.add(FieldProblem.of("status", FieldErrorCode.UNKNOWN_VALUE));
		}
		if (hasValues(criteria.situations()) && !SITUATIONS.containsAll(upperCase(criteria.situations()))) {
			problems.add(FieldProblem.of("situation", FieldErrorCode.UNKNOWN_VALUE));
		}
		if (!problems.isEmpty()) {
			throw new ValidationException(MESSAGE_SCOPE, problems);
		}
	}

	private ResourceListItemResponse item(RosterEntry row, LocalDate today) {
		boolean hasProject = row.getProjectId() != null;
		LocalDate end = row.getProjectEndDate();
		return new ResourceListItemResponse(row.getId(), row.getEmployeeCode(), row.getFullName(), row.getLevel(),
				row.getClientRole(), row.getLeaderId() == null ? null : new IdName(row.getLeaderId(), row.getLeaderName()),
				new CodeLabel(row.getStatusCode(), row.getStatusName()),
				new CodeLabel(row.getSituation(), messages.getMessage("resource.situation." + row.getSituation(), null,
						row.getSituation(), LocaleContextHolder.getLocale())),
				row.getHireDate(), row.isOnProbation(), row.getProbationEndDate(), row.getProjectId(),
				hasProject ? row.getAssignmentStartDate() : null, end, hasProject && end == null,
				end == null ? null : ChronoUnit.DAYS.between(today, end));
	}

	/**
	 * The groups of the rows of a page, in order, each with how many rows it has in all the pages, so the
	 * screen can show "2 colaboradores" or "1 de 2" on a group cut by the page end.
	 */
	private List<ResourceGroupResponse> groups(List<RosterEntry> rows, Specification<RosterEntry> filters) {
		Map<Long, RosterEntry> firstOfGroup = new LinkedHashMap<>();
		rows.forEach(row -> firstOfGroup.putIfAbsent(row.getProjectId(), row));
		return firstOfGroup.values()
			.stream()
			.map(row -> new ResourceGroupResponse(groupType(row), row.getProjectId(), row.getProjectName(),
					row.getClientId() == null ? null : new IdName(row.getClientId(), row.getClientName()),
					row.getContractEndDate(),
					roster.count(filters.and(RosterEntrySpecifications.inGroup(row.getProjectId())))))
			.toList();
	}

	private static String groupType(RosterEntry row) {
		if (row.getProjectId() == null) {
			return GROUP_NO_PROJECT;
		}
		return Boolean.TRUE.equals(row.getProjectActive()) ? GROUP_PROJECT : GROUP_NOT_SPECIFIED;
	}

	private static void addEqual(List<Specification<RosterEntry>> filters, String attribute, Object value) {
		if (value != null) {
			filters.add(RosterEntrySpecifications.equalTo(attribute, value));
		}
	}

	private static boolean hasValues(List<String> values) {
		return values != null && values.stream().anyMatch(value -> value != null && !value.isBlank());
	}

	private static List<String> upperCase(List<String> values) {
		return values.stream()
			.filter(Objects::nonNull)
			.map(String::strip)
			.filter(value -> !value.isEmpty())
			.map(value -> value.toUpperCase(Locale.ROOT))
			.toList();
	}

}
