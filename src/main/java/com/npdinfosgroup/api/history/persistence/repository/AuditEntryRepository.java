package com.npdinfosgroup.api.history.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.history.persistence.entity.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Rows of the audit log.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {

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

	/**
	 * Users who appear in the log of a record, for the "Usuario" filter.
	 *
	 * @param entityType audited table
	 * @param entityId id of the record
	 * @return their usernames, in alphabetical order
	 */
	@Query("select distinct e.actor from AuditEntry e where e.entityType = :entityType and e.entityId = :entityId order by e.actor")
	List<String> findActors(@Param("entityType") String entityType, @Param("entityId") Long entityId);

	/**
	 * Fields edited at least once in a record, for the "Campo" filter. Fields only set at registration
	 * are left out, because the filter never shows the registration.
	 *
	 * @param entityType audited table
	 * @param entityId id of the record
	 * @return their technical names
	 */
	@Query("select distinct e.field from AuditEntry e where e.entityType = :entityType and e.entityId = :entityId and e.action = 'UPDATE'")
	List<String> findEditedFields(@Param("entityType") String entityType, @Param("entityId") Long entityId);

}
