package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.TerminationReason;

/**
 * Termination reasons of an employee on Baja.
 */
public interface TerminationReasonRepository extends CatalogEntityRepository<TerminationReason, Short> {

	@Override
	default Catalog catalog() {
		return Catalog.TERMINATION_REASONS;
	}

}
