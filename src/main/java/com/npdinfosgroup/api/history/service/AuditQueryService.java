package com.npdinfosgroup.api.history.service;

import java.util.Optional;

import com.npdinfosgroup.api.history.dto.response.LastChangeResponse;
import com.npdinfosgroup.api.history.persistence.repository.AuditEntryRepository;
import com.npdinfosgroup.api.history.service.mapper.AuditMapper;
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

	private final AuditEntryRepository repository;

	private final AuditMapper mapper;

	/**
	 * Creates the service.
	 *
	 * @param repository audit rows
	 * @param mapper builds the response
	 */
	AuditQueryService(AuditEntryRepository repository, AuditMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
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

}
