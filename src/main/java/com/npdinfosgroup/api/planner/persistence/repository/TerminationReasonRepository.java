package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.Catalog;
import com.npdinfosgroup.api.planner.persistence.entity.TerminationReason;

/**
 * Termination reasons of an employee on Baja.
 */
public interface TerminationReasonRepository extends CatalogEntityRepository<TerminationReason, Short> {

	@Override
	default Catalog catalog() {
		return Catalog.TERMINATION_REASONS;
	}

	/**
	 * The reason with a code, active or not.
	 *
	 * @param code code of the reason, such as {@code CAMBIO_DE_ROL}
	 * @return the reason, if it exists
	 */
	Optional<TerminationReason> findByCode(String code);

}
