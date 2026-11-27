package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Clients of Infosgroup.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

	/**
	 * Clients by name, active or inactive.
	 *
	 * @param active {@code true} for the active ones, {@code false} for the inactive ones
	 * @return the clients
	 */
	List<Client> findByActiveOrderByNameAsc(boolean active);

	/**
	 * Client with a name, ignoring case like its unique index.
	 *
	 * @param name client name
	 * @return the client, if the name is taken
	 */
	Optional<Client> findFirstByNameIgnoreCase(String name);

	/**
	 * How many clients are active, for the status bar (HU-11). The imported «No especificado» client is
	 * inactive, so it is not counted.
	 *
	 * @return the number of active clients
	 */
	long countByActiveTrue();

}
