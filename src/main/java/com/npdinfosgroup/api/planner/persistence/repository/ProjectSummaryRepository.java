package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.List;

import com.npdinfosgroup.api.planner.persistence.entity.ProjectSummary;
import org.springframework.data.repository.Repository;

/**
 * Projects with their team size already counted, from the {@code project_summary_v} view (read only).
 */
public interface ProjectSummaryRepository extends Repository<ProjectSummary, Long> {

	/**
	 * The active projects of a client, by name.
	 *
	 * @param clientId client id
	 * @return its active projects
	 */
	List<ProjectSummary> findByClientIdAndActiveTrueOrderByNameAsc(Long clientId);

}
