package com.npdinfosgroup.api.history.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.npdinfosgroup.api.history.dto.AuditChange;
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

	private static final String UPDATE = "UPDATE";

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
			.map(value -> entry(entityType, entityId, CREATE, value.getKey(), null, value.getValue(), actor, now))
			.toList();
		repository.saveAll(entries);
	}

	/**
	 * Records an edit: one row per field whose value changed, with the previous and the new value.
	 * Fields that kept their value are not recorded, so an edit that changed nothing leaves no rows.
	 *
	 * @param entityType audited table, for example {@code employee}
	 * @param entityId id of the edited record
	 * @param before values before the edit, as the user sees them, by field name; a missing field or
	 *        a {@code null} value means the field was empty
	 * @param after values after the edit, in the same form
	 * @param actor username of who edited it
	 * @return how many fields changed
	 */
	@Transactional
	public int recordChanges(String entityType, long entityId, Map<String, String> before, Map<String, String> after,
			String actor) {
		Instant now = clock.instant();
		// Fields of both snapshots, in their order: a field can appear only after the edit (a new project)
		// or only before it (a project left on Baja)
		Set<String> fields = new LinkedHashSet<>(before.keySet());
		fields.addAll(after.keySet());
		List<AuditEntry> entries = fields.stream()
			.filter(field -> !Objects.equals(before.get(field), after.get(field)))
			.map(field -> entry(entityType, entityId, UPDATE, field, before.get(field), after.get(field), actor, now))
			.toList();
		repository.saveAll(entries);
		return entries.size();
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

	/**
	 * The last change of a record: who made it, when, and which fields it touched. Every field saved in
	 * one creation or edit shares its moment, so they are read together.
	 *
	 * @param entityType audited table
	 * @param entityId id of the record
	 * @return the change, or empty if the record was never changed through the API (for example, it came
	 *         from the planner import)
	 */
	@Transactional(readOnly = true)
	public Optional<AuditChange> findLastChange(String entityType, long entityId) {
		return repository.findFirstByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(entityType, entityId)
			.map(last -> new AuditChange(last.getActor(), last.getOccurredAt(), last.getAction(),
					repository.findByEntityTypeAndEntityIdAndOccurredAtOrderByIdAsc(entityType, entityId,
							last.getOccurredAt())
						.stream()
						.map(AuditEntry::getField)
						.toList()));
	}

	private static AuditEntry entry(String entityType, long entityId, String action, String field, String oldValue,
			String newValue, String actor, Instant occurredAt) {
		AuditEntry entry = new AuditEntry();
		entry.setEntityType(entityType);
		entry.setEntityId(entityId);
		entry.setAction(action);
		entry.setField(field);
		entry.setOldValue(oldValue);
		entry.setNewValue(newValue);
		entry.setActor(actor);
		entry.setOccurredAt(occurredAt);
		return entry;
	}

}
