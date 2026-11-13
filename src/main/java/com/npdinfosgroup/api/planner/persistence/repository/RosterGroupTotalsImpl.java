package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.HashMap;
import java.util.Map;

import com.npdinfosgroup.api.planner.persistence.entity.RosterEntry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

/**
 * Implementation of {@link RosterGroupTotals}, plugged into {@link RosterEntryRepository} by Spring Data.
 * It uses the Criteria API so the search filters, built as {@link Specification}s, are written once.
 */
class RosterGroupTotalsImpl implements RosterGroupTotals {

	private final EntityManager entityManager;

	/**
	 * Creates the implementation.
	 *
	 * @param entityManager JPA entity manager
	 */
	RosterGroupTotalsImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Map<Long, Long> countByGroup(Specification<RosterEntry> filters) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<RosterEntry> root = query.from(RosterEntry.class);
		Expression<Long> projectId = root.get("projectId");
		query.multiselect(projectId, cb.count(root)).groupBy(projectId);
		Predicate where = filters.toPredicate(root, query, cb);
		if (where != null) {
			query.where(where);
		}
		// HashMap, unlike Map.of or Collectors.toMap, accepts the null key of the people without project
		Map<Long, Long> totals = new HashMap<>();
		entityManager.createQuery(query).getResultList().forEach(row -> totals.put(row.get(0, Long.class),
				row.get(1, Long.class)));
		return totals;
	}

}
