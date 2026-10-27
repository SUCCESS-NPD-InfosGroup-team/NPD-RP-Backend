package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.Catalog;
import com.npdinfosgroup.api.planner.persistence.entity.JobPosition;

/**
 * Positions at Infosgroup.
 */
public interface JobPositionRepository extends CatalogEntityRepository<JobPosition, Long> {

	@Override
	default Catalog catalog() {
		return Catalog.POSITIONS;
	}

}
