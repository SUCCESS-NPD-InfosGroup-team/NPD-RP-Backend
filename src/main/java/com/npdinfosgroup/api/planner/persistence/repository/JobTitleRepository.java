package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.JobTitle;

/**
 * Job titles (PUESTO).
 */
public interface JobTitleRepository extends CatalogEntityRepository<JobTitle, Long> {

	@Override
	default Catalog catalog() {
		return Catalog.JOB_TITLES;
	}

}
