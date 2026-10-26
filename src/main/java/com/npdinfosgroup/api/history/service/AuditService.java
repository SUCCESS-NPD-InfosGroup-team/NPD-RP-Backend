package com.npdinfosgroup.api.history.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.npdinfosgroup.api.history.persistence.entity.AuditEntry;
import com.npdinfosgroup.api.history.persistence.repository.AuditEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records who changed what and when, for the modules that edit data.
 * <p>
 * Writes join the caller's transaction, so a change and its audit rows are saved together or not at
 * all.
 */
@Service
public class AuditService {

	private static final String CREATE = "CREATE";

	private final AuditEntryRepository repository;

	private final Clock clock;

	/**
	 * Creates the service.
	 *
	 * @param repository audit rows
	 * @param clock gives the moment of each change
	 */
	AuditService(AuditEntryRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * Records the creation of a record: one row per field with a value, with no previous value.
	 *
	 * @param entityType audited table, for example {@code employee}
	 * @param entityId id of the new record
	 * @param initialValues values as the user sees them, by field name; {@code null} values are skipped
	 * @param actor username of who created it
	 */
	@Transactional
	public void recordCreation(String entityType, long entityId, Map<String, String> initialValues, String actor) {
		Instant now = clock.instant();
		List<AuditEntry> entries = initialValues.entrySet()
			.stream()
			.filter(value -> Objects.nonNull(value.getValue()))
			.map(value -> entry(entityType, entityId, value.getKey(), value.getValue(), actor, now))
			.toList();
		repository.saveAll(entries);
	}

	/**
	 * Who changed a record last.
	 *
	 * @param entityType audited table
	 * @param entityId id of the record
	 * @return the username, or empty if the record was never changed through the API (for example,
	 *         it came from the planner import)
	 */
	@Transactional(readOnly = true)
	public Optional<String> findLastActor(String entityType, long entityId) {
		return repository.findFirstByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(entityType, entityId)
			.map(AuditEntry::getActor);
	}

	private static AuditEntry entry(String entityType, long entityId, String field, String newValue, String actor,
			Instant occurredAt) {
		AuditEntry entry = new AuditEntry();
		entry.setEntityType(entityType);
		entry.setEntityId(entityId);
		entry.setAction(CREATE);
		entry.setField(field);
		entry.setNewValue(newValue);
		entry.setActor(actor);
		entry.setOccurredAt(occurredAt);
		return entry;
	}

}
