package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Projects of the clients.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

	/**
	 * Project of a client with a name, ignoring case like its unique index.
	 *
	 * @param clientId client id
	 * @param name project name
	 * @return the project, if the client already has one with that name
	 */
	Optional<Project> findFirstByClient_IdAndNameIgnoreCase(Long clientId, String name);

	/**
	 * Another project of a client with a name, ignoring case, for the rename of a project.
	 *
	 * @param clientId client id
	 * @param name project name
	 * @param id the project being renamed, which does not count
	 * @return the other project, if the client already has one with that name
	 */
	Optional<Project> findFirstByClient_IdAndNameIgnoreCaseAndIdNot(Long clientId, String name, Long id);

	/**
	 * How many projects are active, for the status bar (HU-11).
	 *
	 * @return the number of active projects
	 */
	long countByActiveTrue();

}
