package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.ClientRole;

/**
 * Roles at the client.
 */
public interface ClientRoleRepository extends CatalogEntityRepository<ClientRole, Long> {

	@Override
	default Catalog catalog() {
		return Catalog.CLIENT_ROLES;
	}

}
