package com.npdinfosgroup.api.history.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.history.persistence.entity.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Rows of the audit log.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

	/**
	 * Latest change of a record.
	 *
	 * @param entityType audited table
	 * @param entityId id of the record
	 * @return the most recent row, if the record was ever audited
	 */
	Optional<AuditEntry> findFirstByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(String entityType,
			Long entityId);

	/**
	 * The rows of one change of a record: every field saved in the same transaction shares its moment.
	 *
	 * @param entityType audited table
	 * @param entityId id of the record
	 * @param occurredAt moment of the change
	 * @return its rows, in the order they were written
	 */
	List<AuditEntry> findByEntityTypeAndEntityIdAndOccurredAtOrderByIdAsc(String entityType, Long entityId,
			Instant occurredAt);

}
