package com.npdinfosgroup.api.planner.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.npdinfosgroup.api.planner.persistence.entity.CatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/**
 * Queries shared by every catalog of the planner: the values that can still be chosen.
 * <p>
 * Inactive values, such as "No especificado", stay in the table for old records but are never offered.
 * Each repository says which {@link Catalog} it serves, so the catalog service finds it without
 * knowing every repository.
 *
 * @param <T> catalog entity
 * @param <I> type of its id
 */
@NoRepositoryBean
public interface CatalogEntityRepository<T extends CatalogEntity, I> extends JpaRepository<T, I> {

	/**
	 * The catalog this repository serves.
	 *
	 * @return the catalog
	 */
	Catalog catalog();

	/**
	 * Active values in the order they were seeded, for the fixed catalogs.
	 *
	 * @return the active values
	 */
	List<T> findByActiveTrueOrderByIdAsc();

	/**
	 * Active values by name, for the catalogs that grow with the data.
	 *
	 * @return the active values
	 */
	List<T> findByActiveTrueOrderByNameAsc();

	/**
	 * Whether a value is active, whatever the type of the catalog's id.
	 *
	 * @param id id of the value
	 * @return {@code true} if active, {@code false} if inactive, empty if it does not exist
	 */
	@Query("SELECT c.active FROM #{#entityName} c WHERE CAST(c.id AS Long) = :id")
	Optional<Boolean> findActiveById(@Param("id") long id);

}
