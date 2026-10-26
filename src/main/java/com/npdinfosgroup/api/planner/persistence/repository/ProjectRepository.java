package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Projects of the clients.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {
}
