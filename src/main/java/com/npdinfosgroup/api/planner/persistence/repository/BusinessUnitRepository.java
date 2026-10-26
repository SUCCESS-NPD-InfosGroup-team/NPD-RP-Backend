package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.BusinessUnit;
import com.npdinfosgroup.api.planner.persistence.entity.Catalog;

/**
 * Business units (UEN).
 */
public interface BusinessUnitRepository extends CatalogEntityRepository<BusinessUnit, Short> {

	@Override
	default Catalog catalog() {
		return Catalog.BUSINESS_UNITS;
	}

}
