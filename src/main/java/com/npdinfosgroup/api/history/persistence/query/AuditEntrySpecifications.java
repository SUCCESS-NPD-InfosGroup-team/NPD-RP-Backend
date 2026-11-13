package com.npdinfosgroup.api.history.persistence.query;

import java.time.Instant;

import com.npdinfosgroup.api.history.persistence.entity.AuditEntry;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters of the audit log of one record. Each one narrows the rows; the log combines them so a row must
 * match all of them.
 */
public final class AuditEntrySpecifications {

	private static final String CREATE = "CREATE";

	private static final String UPDATE = "UPDATE";

	private static final String ACTION = "action";

	private static final String ENTITY_TYPE = "entityType";

	private static final String ENTITY_ID = "entityId";

	private static final String OCCURRED_AT = "occurredAt";

	private AuditEntrySpecifications() {
	}

	/**
	 * Rows of one record.
	 *
	 * @param entityType audited table, for example {@code employee}
	 * @param entityId id of the record
	 * @return the filter
	 */
	public static Specification<AuditEntry> ofRecord(String entityType, long entityId) {
		return (root, query, cb) -> cb.and(cb.equal(root.get(ENTITY_TYPE), entityType),
				cb.equal(root.get(ENTITY_ID), entityId));
	}

	/**
	 * One row per entry of the log: every edited field, plus only the first row of the registration, which
	 * stands for the whole registration ("Alta"). So the page and its totals count the registration once.
	 *
	 * @return the filter
	 */
	public static Specification<AuditEntry> entries() {
		return (root, query, cb) -> {
			Subquery<Long> registration = query.subquery(Long.class);
			Root<AuditEntry> created = registration.from(AuditEntry.class);
			registration.select(cb.min(created.<Long>get("id")))
				.where(cb.equal(created.get(ENTITY_TYPE), root.get(ENTITY_TYPE)),
						cb.equal(created.get(ENTITY_ID), root.get(ENTITY_ID)), cb.equal(created.get(ACTION), CREATE));
			return cb.or(cb.equal(root.get(ACTION), UPDATE), cb.equal(root.get("id"), registration));
		};
	}

	/**
	 * Rows written by one user.
	 *
	 * @param actor Keycloak username
	 * @return the filter
	 */
	public static Specification<AuditEntry> byActor(String actor) {
		return (root, query, cb) -> cb.equal(root.get("actor"), actor);
	}

	/**
	 * Edits of one field. The registration is left out: it is not a change of that field.
	 *
	 * @param field technical name of the field, for example {@code assignment.endDate}
	 * @return the filter
	 */
	public static Specification<AuditEntry> editsOf(String field) {
		return (root, query, cb) -> cb.and(cb.equal(root.get(ACTION), UPDATE), cb.equal(root.get("field"), field));
	}

	/**
	 * Rows written at or after a moment.
	 *
	 * @param from first moment included
	 * @return the filter
	 */
	public static Specification<AuditEntry> since(Instant from) {
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(OCCURRED_AT), from);
	}

	/**
	 * Rows written before a moment.
	 *
	 * @param until first moment left out
	 * @return the filter
	 */
	public static Specification<AuditEntry> before(Instant until) {
		return (root, query, cb) -> cb.lessThan(root.get(OCCURRED_AT), until);
	}

}
