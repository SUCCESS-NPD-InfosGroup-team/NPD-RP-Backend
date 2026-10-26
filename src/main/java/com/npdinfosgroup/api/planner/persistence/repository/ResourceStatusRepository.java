package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.ResourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Employment statuses (Activo, Licencia, Baja). Not one of the {@link Catalog}s: the API reads and
 * writes them by code, not by id.
 */
public interface ResourceStatusRepository extends JpaRepository<ResourceStatus, Short> {

	/**
	 * Active statuses in their usual order (Activo, Licencia, Baja).
	 *
	 * @return the statuses
	 */
	List<ResourceStatus> findByActiveTrueOrderByIdAsc();

	/**
	 * An active status by its code.
	 *
	 * @param code status code (ACTIVO, LICENCIA, BAJA)
	 * @return the status, or empty if there is no active status with that code
	 */
	Optional<ResourceStatus> findByCodeAndActiveTrue(String code);

}
