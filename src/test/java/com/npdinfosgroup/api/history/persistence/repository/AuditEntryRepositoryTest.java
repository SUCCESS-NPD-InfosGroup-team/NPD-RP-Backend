package com.npdinfosgroup.api.history.persistence.repository;

import static com.npdinfosgroup.api.history.persistence.query.AuditEntrySpecifications.before;
import static com.npdinfosgroup.api.history.persistence.query.AuditEntrySpecifications.byActor;
import static com.npdinfosgroup.api.history.persistence.query.AuditEntrySpecifications.editsOf;
import static com.npdinfosgroup.api.history.persistence.query.AuditEntrySpecifications.entries;
import static com.npdinfosgroup.api.history.persistence.query.AuditEntrySpecifications.ofRecord;
import static com.npdinfosgroup.api.history.persistence.query.AuditEntrySpecifications.since;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.history.persistence.entity.AuditEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Queries of the audit log of one record (HU-04), against a real PostgreSQL: one row per entry, the
 * filters and the options of the filters.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class AuditEntryRepositoryTest {

	private static final String EMPLOYEE = "employee";

	private static final long RESOURCE = 920_001;

	private static final Instant REGISTERED = Instant.parse("2026-08-03T13:15:00Z");

	private static final Instant EDITED = Instant.parse("2026-10-02T15:14:00Z");

	@Autowired
	private AuditEntryRepository repository;

	private final List<AuditEntry> saved = new ArrayList<>();

	/** A registration of two fields and an edit of two fields by another user. */
	@BeforeEach
	void writeLog() {
		save(EMPLOYEE, RESOURCE, "CREATE", "employeeNumber", "rp.maria", REGISTERED);
		save(EMPLOYEE, RESOURCE, "CREATE", "name", "rp.maria", REGISTERED);
		save(EMPLOYEE, RESOURCE, "UPDATE", "assignment.leader", "rp.jorge", EDITED);
		save(EMPLOYEE, RESOURCE, "UPDATE", "name", "rp.jorge", EDITED);
		save(EMPLOYEE, RESOURCE + 1, "UPDATE", "name", "rp.other", EDITED);
		save("project", RESOURCE, "UPDATE", "name", "rp.other", EDITED);
	}

	/** Removes the rows the tests wrote. */
	@AfterEach
	void cleanUp() {
		repository.deleteAll(saved);
	}

	/** Every edited field plus only the first row of the registration; other records are ignored. */
	@Test
	void selectsOneRowPerEntry() {
		assertThat(fields(ofRecord(EMPLOYEE, RESOURCE).and(entries())))
			.containsExactly("employeeNumber", "assignment.leader", "name");
		assertThat(repository.count(ofRecord(EMPLOYEE, RESOURCE).and(entries()))).isEqualTo(3);
	}

	/** The user filter applies to the registration and to the edits. */
	@Test
	void filtersByUser() {
		assertThat(fields(ofRecord(EMPLOYEE, RESOURCE).and(entries()).and(byActor("rp.maria"))))
			.containsExactly("employeeNumber");
	}

	/** A field filter returns its edits only, never the registration that also set it. */
	@Test
	void filtersTheEditsOfAField() {
		assertThat(fields(ofRecord(EMPLOYEE, RESOURCE).and(entries()).and(editsOf("name")))).containsExactly("name");
		assertThat(repository.findAll(ofRecord(EMPLOYEE, RESOURCE).and(editsOf("name"))))
			.extracting(AuditEntry::getAction)
			.containsExactly("UPDATE");
	}

	/** {@code since} includes its moment and {@code before} leaves its moment out. */
	@Test
	void filtersByMoment() {
		assertThat(fields(ofRecord(EMPLOYEE, RESOURCE).and(entries()).and(since(EDITED))))
			.containsExactly("assignment.leader", "name");
		assertThat(fields(ofRecord(EMPLOYEE, RESOURCE).and(entries()).and(before(EDITED))))
			.containsExactly("employeeNumber");
	}

	/** The users of a record in alphabetical order, and only the fields that were edited. */
	@Test
	void listsTheOptionsOfTheFilters() {
		assertThat(repository.findActors(EMPLOYEE, RESOURCE)).containsExactly("rp.jorge", "rp.maria");
		assertThat(repository.findEditedFields(EMPLOYEE, RESOURCE))
			.containsExactlyInAnyOrder("assignment.leader", "name");
	}

	private List<String> fields(Specification<AuditEntry> filter) {
		return repository.findAll(filter, Sort.by("id")).stream().map(AuditEntry::getField).toList();
	}

	private void save(String entityType, long entityId, String action, String field, String actor,
			Instant occurredAt) {
		AuditEntry entry = new AuditEntry();
		entry.setEntityType(entityType);
		entry.setEntityId(entityId);
		entry.setAction(action);
		entry.setField(field);
		entry.setNewValue("value");
		entry.setActor(actor);
		entry.setOccurredAt(occurredAt);
		saved.add(repository.save(entry));
	}

}
