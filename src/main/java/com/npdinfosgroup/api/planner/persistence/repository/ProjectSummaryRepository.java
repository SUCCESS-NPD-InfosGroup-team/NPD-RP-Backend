package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.ProjectSummary;
import com.npdinfosgroup.api.planner.persistence.query.ProjectSummarySpecifications;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Projects with their team counts already calculated, from the {@code project_summary_v} view (read
 * only). The filters of the projects list are {@link ProjectSummarySpecifications}.
 */
public interface ProjectSummaryRepository
		extends Repository<ProjectSummary, Long>, JpaSpecificationExecutor<ProjectSummary> {

	/**
	 * A project with its counts.
	 *
	 * @param id project id
	 * @return the project, or empty if it does not exist
	 */
	Optional<ProjectSummary> findById(Long id);

	/**
	 * The active projects of a client, by name.
	 *
	 * @param clientId client id
	 * @return its active projects
	 */
	List<ProjectSummary> findByClientIdAndActiveTrueOrderByNameAsc(Long clientId);

}
