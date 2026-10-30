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
	 * Rows of one group of the roster: a project, or the people without project.
	 *
	 * @param projectId project id, or {@code null} for the people without project
	 * @return the filter
	 */
	public static Specification<RosterEntry> inGroup(Long projectId) {
		return projectId == null ? (root, query, cb) -> cb.isNull(root.get("projectId"))
				: equalTo("projectId", projectId);
	}

}
