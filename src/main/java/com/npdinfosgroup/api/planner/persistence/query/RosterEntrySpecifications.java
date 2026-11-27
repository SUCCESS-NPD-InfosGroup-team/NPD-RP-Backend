package com.npdinfosgroup.api.planner.persistence.query;

import java.util.Collection;

import com.npdinfosgroup.api.planner.persistence.entity.RosterEntry;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters of the roster search. Each one narrows the rows; the search combines them so a row must match
 * all of them.
 */
public final class RosterEntrySpecifications {

	private static final char LIKE_ESCAPE = '\\';

	private static final String PROJECT_ID = "projectId";

	private RosterEntrySpecifications() {
	}

	/**
	 * Rows whose name, employee number, project or client contain a text.
	 *
	 * @param normalizedText text in lower case and without accents, like the {@code search_text} column
	 * @return the filter
	 */
	public static Specification<RosterEntry> containing(String normalizedText) {
		String pattern = "%" + normalizedText.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
		return (root, query, cb) -> cb.like(root.get("searchText"), pattern, LIKE_ESCAPE);
	}

	/**
	 * Rows with a value in a column.
	 *
	 * @param attribute attribute of {@link RosterEntry}, for example {@code clientId}
	 * @param value the value
	 * @return the filter
	 */
	public static Specification<RosterEntry> equalTo(String attribute, Object value) {
		return (root, query, cb) -> cb.equal(root.get(attribute), value);
	}

	/**
	 * Rows whose column holds one of some values.
	 *
	 * @param attribute attribute of {@link RosterEntry}, for example {@code statusCode}
	 * @param values the accepted values
	 * @return the filter
	 */
	public static Specification<RosterEntry> in(String attribute, Collection<?> values) {
		return (root, query, cb) -> root.get(attribute).in(values);
	}

	/**
	 * People who can be added to a project: everyone not on Baja and not already in it, with or without
	 * another project.
	 *
	 * @param projectId the project
	 * @param terminatedStatus status code of the people left out (Baja)
	 * @return the filter
	 */
	public static Specification<RosterEntry> candidatesFor(long projectId, String terminatedStatus) {
		return (root, query, cb) -> cb.and(cb.notEqual(root.get("statusCode"), terminatedStatus),
				cb.or(cb.isNull(root.get(PROJECT_ID)), cb.notEqual(root.get(PROJECT_ID), projectId)));
	}

	/**
	 * People without a project.
	 *
	 * @return the filter
	 */
	public static Specification<RosterEntry> withoutProject() {
		return (root, query, cb) -> cb.isNull(root.get(PROJECT_ID));
	}

	/**
	 * Orders the rows: people without a project first, then by name. It does not filter, nor order a count.
	 *
	 * @return the ordering, as a filter that matches every row
	 */
	public static Specification<RosterEntry> withoutProjectFirst() {
		return (root, query, cb) -> {
			if (query != null && !Long.class.equals(query.getResultType())) {
				query.orderBy(cb.asc(cb.selectCase().when(cb.isNull(root.get(PROJECT_ID)), 0).otherwise(1)),
						cb.asc(root.get("fullName")), cb.asc(root.get("id")));
			}
			return cb.conjunction();
		};
	}

}
