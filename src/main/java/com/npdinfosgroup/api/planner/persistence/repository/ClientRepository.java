package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Clients of Infosgroup.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {
}
