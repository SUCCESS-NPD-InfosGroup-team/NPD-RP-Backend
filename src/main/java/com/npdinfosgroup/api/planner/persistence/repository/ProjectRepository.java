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

}
