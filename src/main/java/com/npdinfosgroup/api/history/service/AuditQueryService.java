package com.npdinfosgroup.api.history.service;

import java.text.Collator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.history.dto.request.AuditLogCriteria;
import com.npdinfosgroup.api.history.dto.response.AuditFieldOption;
import com.npdinfosgroup.api.history.dto.response.AuditLogEntryResponse;
import com.npdinfosgroup.api.history.dto.response.AuditLogResponse;
import com.npdinfosgroup.api.history.dto.response.LastChangeResponse;
import com.npdinfosgroup.api.history.persistence.entity.AuditEntry;
import com.npdinfosgroup.api.history.persistence.query.AuditEntrySpecifications;
import com.npdinfosgroup.api.history.persistence.repository.AuditEntryRepository;
import com.npdinfosgroup.api.history.service.mapper.AuditMapper;
import com.npdinfosgroup.api.shared.dto.PageInfo;
import com.npdinfosgroup.api.shared.exception.FieldErrorCode;
import com.npdinfosgroup.api.shared.exception.FieldProblem;
import com.npdinfosgroup.api.shared.exception.ValidationException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the audit log for the API. Writing it stays in {@link AuditService}, which the modules that
 * edit data call inside their own transactions.
 */
@Service
public class AuditQueryService {

	/** The audit log names resources by their table, {@code employee}. */
	static final String RESOURCE = "employee";

	private static final String CREATE = "CREATE";

	/** Scope of the field messages of the log ({@code validation.auditLog.<field>.<CODE>}). */
	private static final String MESSAGE_SCOPE = "auditLog";

	/** Newest first; inside one operation, which shares its moment, the order of the form. */
	private static final Sort LOG_ORDER = Sort.by(Order.desc("occurredAt"), Order.asc("id"));

	private final AuditEntryRepository repository;

	private final AuditMapper mapper;

	private final Clock clock;

	/**
	 * Creates the service.
	 *
	 * @param repository audit rows
	 * @param mapper builds the response
	 * @param clock gives the business time zone, in which the date filters are whole days
	 */
	AuditQueryService(AuditEntryRepository repository, AuditMapper mapper, Clock clock) {
		this.repository = repository;
		this.mapper = mapper;
		this.clock = clock;
	}

	/**
	 * The last operation saved through the API on a resource (its registration or its latest edit), with
	 * every field it changed. Older operations are never read.
	 * <p>
	 * Every row of one operation shares its moment, so the latest row gives the moment and a second
	 * query its rows. Both use the index {@code audit_log_entity_idx}.
	 *
	 * @param resourceId employee id
	 * @return the operation, or empty if the resource was never changed through the API (it came from
	 *         the import) or does not exist
	 */
	@Transactional(readOnly = true)
	public Optional<LastChangeResponse> findResourceLastChange(long resourceId) {
		return repository.findFirstByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(RESOURCE, resourceId)
			.map(last -> mapper.toLastChange(repository.findByEntityTypeAndEntityIdAndOccurredAtOrderByIdAsc(RESOURCE,
					resourceId, last.getOccurredAt())));
	}

	/**
	 * A page of the audit log of a resource (HU-04), newest first, with the options of its filters.
	 * <p>
	 * Each edited field is one entry. The registration is one entry too, "Alta", with every value it was
	 * registered with: the page query only counts its first row, and the rest are read when it is on the
	 * page.
	 *
	 * @param resourceId employee id
	 * @param criteria the filters; {@code null} ones are not applied. The dates are whole days of the
	 *        business time zone, both included. A field filter leaves out the registration.
	 * @param pageable page number and size; its sort is ignored, the order is always the same
	 * @return the entries and the page; an empty page if the resource was never changed through the API
	 *         or does not exist
	 * @throws ValidationException if {@code to} is before {@code from}
	 */
	@Transactional(readOnly = true)
	public AuditLogResponse findResourceLog(long resourceId, AuditLogCriteria criteria, Pageable pageable) {
		if (criteria.from() != null && criteria.to() != null && criteria.to().isBefore(criteria.from())) {
			throw new ValidationException(MESSAGE_SCOPE,
					List.of(FieldProblem.of("to", FieldErrorCode.CONFLICTING_VALUES)));
		}
		Page<AuditEntry> page = repository.findAll(filters(resourceId, criteria),
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LOG_ORDER));
		List<AuditLogEntryResponse> items = page.getContent().stream().map(row -> toEntry(resourceId, row)).toList();
		Collator collator = Collator.getInstance(LocaleContextHolder.getLocale());
		List<AuditFieldOption> fields = repository.findEditedFields(RESOURCE, resourceId)
			.stream()
			.map(mapper::toFieldOption)
			.sorted(Comparator.comparing(AuditFieldOption::label, collator))
			.toList();
		return new AuditLogResponse(items,
				new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()),
				repository.findActors(RESOURCE, resourceId), fields);
	}

	private Specification<AuditEntry> filters(long resourceId, AuditLogCriteria criteria) {
		List<Specification<AuditEntry>> filters = new ArrayList<>();
		filters.add(AuditEntrySpecifications.ofRecord(RESOURCE, resourceId));
		filters.add(AuditEntrySpecifications.entries());
		if (criteria.actor() != null) {
			filters.add(AuditEntrySpecifications.byActor(criteria.actor()));
		}
		if (criteria.field() != null) {
			filters.add(AuditEntrySpecifications.editsOf(criteria.field()));
		}
		if (criteria.from() != null) {
			filters.add(AuditEntrySpecifications.since(startOf(criteria.from())));
		}
		if (criteria.to() != null) {
			filters.add(AuditEntrySpecifications.before(startOf(criteria.to().plusDays(1))));
		}
		return Specification.allOf(filters);
	}

	private Instant startOf(LocalDate day) {
		return day.atStartOfDay(clock.getZone()).toInstant();
	}

	private AuditLogEntryResponse toEntry(long resourceId, AuditEntry row) {
		if (!CREATE.equals(row.getAction())) {
			return mapper.toLogEntry(List.of(row));
		}
		List<AuditEntry> registration = repository
			.findByEntityTypeAndEntityIdAndOccurredAtOrderByIdAsc(RESOURCE, resourceId, row.getOccurredAt())
			.stream()
			.filter(entry -> CREATE.equals(entry.getAction()))
			.toList();
		return mapper.toLogEntry(registration);
	}

}
