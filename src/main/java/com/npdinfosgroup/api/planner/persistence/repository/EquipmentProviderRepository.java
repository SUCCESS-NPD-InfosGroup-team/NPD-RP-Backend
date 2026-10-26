package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.EquipmentProvider;

/**
 * Who provides the laptop (EQUIPO / LAPTOP).
 */
public interface EquipmentProviderRepository extends CatalogEntityRepository<EquipmentProvider, Short> {

	@Override
	default Catalog catalog() {
		return Catalog.EQUIPMENT;
	}

}
