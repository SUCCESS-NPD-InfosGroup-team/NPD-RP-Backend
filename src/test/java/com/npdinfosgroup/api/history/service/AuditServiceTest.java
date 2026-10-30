package com.npdinfosgroup.api.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.npdinfosgroup.api.history.dto.AuditChange;
import com.npdinfosgroup.api.history.persistence.entity.AuditEntry;
import com.npdinfosgroup.api.history.persistence.repository.AuditEntryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The audit rows written for an edit: only the fields that changed, with both values.
 */
class AuditServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

	private final AuditEntryRepository repository = mock();

	private final AuditService service = new AuditService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

	/**
	 * A changed value, a value that appears and one that disappears are recorded; an unchanged one is
	 * not.
	 */
	@Test
	void recordsOnlyTheChangedFields() {
		Map<String, String> before = new LinkedHashMap<>();
		before.put("name", "Ana Torres");
		before.put("level", "3");
		before.put("assignment.project", "Portal");
		before.put("email", null);
		Map<String, String> after = new LinkedHashMap<>();
		after.put("name", "Ana Torres");
		after.put("level", "4");
		after.put("email", "ana@infosgroup.com");

		int changes = service.recordChanges("employee", 7, before, after, "mperez");

		assertThat(changes).isEqualTo(3);
		assertThat(savedEntries()).allSatisfy(entry -> {
			assertThat(entry.getAction()).isEqualTo("UPDATE");
			assertThat(entry.getEntityType()).isEqualTo("employee");
			assertThat(entry.getEntityId()).isEqualTo(7);
			assertThat(entry.getActor()).isEqualTo("mperez");
			assertThat(entry.getOccurredAt()).isEqualTo(NOW);
		})
			.extracting(entry -> entry.getField() + ": " + entry.getOldValue() + " -> " + entry.getNewValue())
			.containsExactly("level: 3 -> 4", "assignment.project: Portal -> null", "email: null -> ana@infosgroup.com");
	}

	/** An edit that changed nothing leaves no rows. */
	@Test
	void recordsNothingWithoutChanges() {
		Map<String, String> values = new HashMap<>(Map.of("name", "Ana Torres"));

		assertThat(service.recordChanges("employee", 7, values, Map.copyOf(values), "mperez")).isZero();
		assertThat(savedEntries()).isEmpty();
	}

	/** The last change gathers every field saved at its moment. */
	@Test
	void findsTheFieldsOfTheLastChange() {
		AuditEntry name = entry("name");
		when(repository.findFirstByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc("employee", 7L))
			.thenReturn(Optional.of(name));
		when(repository.findByEntityTypeAndEntityIdAndOccurredAtOrderByIdAsc("employee", 7L, NOW))
			.thenReturn(List.of(entry("level"), name));

		assertThat(service.findLastChange("employee", 7))
			.contains(new AuditChange("mperez", NOW, "UPDATE", List.of("level", "name")));
	}

	/** A record that never changed through the API has no last change. */
	@Test
	void hasNoLastChangeWithoutRows() {
		assertThat(service.findLastChange("employee", 8)).isEmpty();
	}

	private static AuditEntry entry(String field) {
		AuditEntry entry = new AuditEntry();
		entry.setField(field);
		entry.setAction("UPDATE");
		entry.setActor("mperez");
		entry.setOccurredAt(NOW);
		return entry;
	}

	@SuppressWarnings("unchecked")
	private List<AuditEntry> savedEntries() {
		ArgumentCaptor<List<AuditEntry>> captor = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(captor.capture());
		return captor.getValue();
	}

}
