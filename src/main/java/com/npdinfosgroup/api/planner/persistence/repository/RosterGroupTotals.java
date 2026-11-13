package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.Map;

import com.npdinfosgroup.api.planner.persistence.entity.RosterEntry;
import org.springframework.data.jpa.domain.Specification;

/**
 * Totals of the roster groups in one query, which Spring Data does not offer with a {@link Specification}.
 */
public interface RosterGroupTotals {

	/**
	 * How many rows of each group of the roster match the filters: one {@code GROUP BY} over
	 * {@code resource_v} instead of one count per group.
	 *
	 * @param filters the same filters as the search
	 * @return rows per project id; the {@code null} key holds the people without project. Groups without
	 * matching rows are absent
	 */
	Map<Long, Long> countByGroup(Specification<RosterEntry> filters);

}
