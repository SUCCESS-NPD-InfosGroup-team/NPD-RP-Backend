package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.ResourceSituation;
import org.springframework.data.repository.Repository;

/**
 * The situation of each resource (Asignado, Pendiente de confirmación, Sin proyecto), calculated by
 * the {@code resource_v} view (read only).
 */
public interface ResourceSituationRepository extends Repository<ResourceSituation, Long> {

	/**
	 * The situation of a resource.
	 *
	 * @param employeeId employee id
	 * @return its situation, or empty if the employee does not exist
	 */
	Optional<ResourceSituation> findById(Long employeeId);

}
