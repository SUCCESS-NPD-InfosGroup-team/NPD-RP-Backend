package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.Gender;

/**
 * Genders of the employees.
 */
public interface GenderRepository extends CatalogEntityRepository<Gender, Short> {

	@Override
	default Catalog catalog() {
		return Catalog.GENDERS;
	}

}
