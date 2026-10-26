package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.WorkModality;

/**
 * Work modalities.
 */
public interface WorkModalityRepository extends CatalogEntityRepository<WorkModality, Short> {

	@Override
	default Catalog catalog() {
		return Catalog.WORK_MODES;
	}

}
