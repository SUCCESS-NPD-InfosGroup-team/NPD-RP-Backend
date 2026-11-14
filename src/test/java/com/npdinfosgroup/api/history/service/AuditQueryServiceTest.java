package com.npdinfosgroup.api.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.npdinfosgroup.api.TestSecurityConfiguration;
import com.npdinfosgroup.api.TestcontainersConfiguration;
import com.npdinfosgroup.api.history.dto.request.AuditLogCriteria;
import com.npdinfosgroup.api.history.dto.response.AuditFieldOption;
import com.npdinfosgroup.api.history.dto.response.AuditLogEntryResponse;
import com.npdinfosgroup.api.history.dto.response.AuditLogResponse;
import com.npdinfosgroup.api.history.dto.response.FieldChangeResponse;
import com.npdinfosgroup.api.history.persistence.entity.AuditEntry;
import com.npdinfosgroup.api.history.persistence.repository.AuditEntryRepository;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * Audit log of a resource (HU-04), against a real PostgreSQL: one entry per edited field, the
 * registration as one entry, order, filters, paging and the options of the filters.
 * <p>
 * The service only reads the log, so the tests write the rows directly and delete them after.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
class AuditQueryServiceTest {

	private static final long RESOURCE = 910_001;

	private static final Instant REGISTERED = Instant.parse("2026-08-03T13:15:00Z");

	/** 23:30 of October 1 in Bogotá (UTC-5), already October 2 in UTC. */
	private static final Instant FIRST_EDIT = Instant.parse("2026-10-02T04:30:00Z");

	private static final Instant SECOND_EDIT = Instant.parse("2026-10-02T15:14:00Z");

	private static final AuditLogCriteria NO_FILTERS = new AuditLogCriteria(null, null, null, null);

	@Autowired
	private AuditQueryService service;

	@Autowired
	private AuditEntryRepository repository;

	private final List<AuditEntry> saved = new ArrayList<>();

	/** A registration of three fields, an edit of two fields and an edit of one, by two users. */
	@BeforeEach
	void writeLog() {
		save("employee", RESOURCE, "CREATE", "employeeNumber", null, "1045", "qs.maria", REGISTERED);
		save("employee", RESOURCE, "CREATE", "name", null, "Ana Torres", "qs.maria", REGISTERED);
		save("employee", RESOURCE, "CREATE", "assignment.endDate", null, "2026-09-30", "qs.maria", REGISTERED);
		save("employee", RESOURCE, "UPDATE", "assignment.leader", "Omar Delgado", "Lorena Batista", "qs.jorge",
				FIRST_EDIT);
		save("employee", RESOURCE, "UPDATE", "name", "Ana Torres", "Ana María Torres", "qs.jorge", FIRST_EDIT);
		save("employee", RESOURCE, "UPDATE", "assignment.endDate", "2026-09-30", "2026-10-31", "qs.maria",
				SECOND_EDIT);
		save("employee", RESOURCE + 1, "UPDATE", "name", "Otro", "Otra", "qs.other", SECOND_EDIT);
		save("project", RESOURCE, "UPDATE", "name", "Old", "New", "qs.other", SECOND_EDIT);
	}

	/** Removes the rows the tests wrote. */
	@AfterEach
	void cleanUp() {
		repository.deleteAll(saved);
	}

	/**
	 * Newest first, one entry per edited field in the order of the form, and the registration as one
	 * entry with every registered value. Rows of another resource or table are ignored.
	 */
	@Test
	void returnsEditsPerFieldAndTheRegistrationAsOneEntry() {
		AuditLogResponse log = service.findResourceLog(RESOURCE, NO_FILTERS, PageRequest.of(0, 25));

		assertThat(log.items()).extracting(AuditLogEntryResponse::action)
			.containsExactly("UPDATE", "UPDATE", "UPDATE", "CREATE");
		assertThat(log.items()).extracting(entry -> entry.fields().getFirst().field())
			.containsExactly("assignment.endDate", "assignment.leader", "name", "employeeNumber");
		AuditLogEntryResponse edit = log.items().getFirst();
		assertThat(edit.actor()).isEqualTo("qs.maria");
		assertThat(edit.occurredAt()).isEqualTo(SECOND_EDIT);
		assertThat(edit.fields()).containsExactly(
				new FieldChangeResponse("assignment.endDate", "Fecha final", "2026-09-30", "2026-10-31"));
		AuditLogEntryResponse registration = log.items().getLast();
		assertThat(registration.id()).isEqualTo(saved.getFirst().getId());
		assertThat(registration.occurredAt()).isEqualTo(REGISTERED);
		assertThat(registration.fields()).extracting(FieldChangeResponse::field)
			.containsExactly("employeeNumber", "name", "assignment.endDate");
		assertThat(registration.fields()).extracting(FieldChangeResponse::oldValue).containsOnlyNulls();
		assertThat(log.page().totalElements()).isEqualTo(4);
	}

	/** The page and its totals count the registration once, and it is completed on the last page. */
	@Test
	void pagesCountTheRegistrationOnce() {
		AuditLogResponse second = service.findResourceLog(RESOURCE, NO_FILTERS, PageRequest.of(1, 2));

		assertThat(second.page().totalElements()).isEqualTo(4);
		assertThat(second.page().totalPages()).isEqualTo(2);
		assertThat(second.items()).extracting(AuditLogEntryResponse::action).containsExactly("UPDATE", "CREATE");
		assertThat(second.items().getLast().fields()).hasSize(3);
	}

	/** The dates are whole days in Bogotá, both included: 23:30 of October 1 is October 1. */
	@Test
	void filtersByBogotaDays() {
		LocalDate october1 = LocalDate.parse("2026-10-01");
		LocalDate october2 = LocalDate.parse("2026-10-02");

		assertThat(service.findResourceLog(RESOURCE, new AuditLogCriteria(null, null, october1, october1),
				PageRequest.of(0, 25))
			.items()).extracting(AuditLogEntryResponse::occurredAt).containsOnly(FIRST_EDIT).hasSize(2);
		assertThat(service.findResourceLog(RESOURCE, new AuditLogCriteria(null, null, october2, null),
				PageRequest.of(0, 25))
			.items()).extracting(AuditLogEntryResponse::occurredAt).containsExactly(SECOND_EDIT);
		assertThat(service.findResourceLog(RESOURCE, new AuditLogCriteria(null, null, null, october1),
				PageRequest.of(0, 25))
			.items()).hasSize(3);
	}

	/** The options of the filters ignore the filters: users and edited fields, by label. */
	@Test
	void listsTheUsersAndTheEditedFields() {
		AuditLogResponse log = service.findResourceLog(RESOURCE,
				new AuditLogCriteria("qs.maria", "assignment.endDate", null, null), PageRequest.of(0, 25));

		assertThat(log.actors()).containsExactly("qs.jorge", "qs.maria");
		assertThat(log.fields()).extracting(AuditFieldOption::label)
			.containsExactly("Fecha final", "Líder NPD", "Nombre");
		assertThat(log.fields()).extracting(AuditFieldOption::field)
			.containsExactly("assignment.endDate", "assignment.leader", "name");
	}

	/** A resource never changed, or one that does not exist, has an empty log. */
	@Test
	void returnsAnEmptyLogForAResourceWithoutChanges() {
		AuditLogResponse log = service.findResourceLog(999_999_999, NO_FILTERS, PageRequest.of(0, 25));

		assertThat(log.items()).isEmpty();
		assertThat(log.page().totalElements()).isZero();
		assertThat(log.actors()).isEmpty();
		assertThat(log.fields()).isEmpty();
	}

	/** "Hasta" before "Desde" is rejected on the {@code to} field. */
	@Test
	void rejectsAnEndDateBeforeTheStartDate() {
		AuditLogCriteria criteria = new AuditLogCriteria(null, null, LocalDate.parse("2026-10-02"),
				LocalDate.parse("2026-10-01"));

		assertThatThrownBy(() -> service.findResourceLog(RESOURCE, criteria, PageRequest.of(0, 25)))
			.isInstanceOfSatisfying(ValidationException.class, error -> {
				assertThat(error.getScope()).isEqualTo("auditLog");
				assertThat(error.getProblems()).singleElement().satisfies(problem -> {
					assertThat(problem.field()).isEqualTo("to");
					assertThat(problem.code()).isEqualTo(FieldErrorCode.CONFLICTING_VALUES);
				});
			});
	}

	private void save(String entityType, long entityId, String action, String field, String oldValue,
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
		saved.add(repository.save(entry));
	}

}
