package com.npdinfosgroup.api.planner.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.npdinfosgroup.api.planner.dto.response.CodeLabel;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.ItemsResponse;
import com.npdinfosgroup.api.planner.persistence.entity.Catalog;
import com.npdinfosgroup.api.planner.persistence.entity.CatalogEntity;
import com.npdinfosgroup.api.planner.persistence.entity.ResourceStatus;
import com.npdinfosgroup.api.planner.persistence.repository.CatalogEntityRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ResourceStatusRepository;
import com.npdinfosgroup.api.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalogs of the forms: the values that can be chosen in each one, and whether a chosen value is
 * still valid.
 * <p>
 * Finds each catalog's repository by the {@link Catalog} it declares, so adding a catalog only takes
 * its entity and its repository.
 */
@Service
public class CatalogService {

	private final Map<Catalog, CatalogEntityRepository<?, ?>> repositories = new EnumMap<>(Catalog.class);

	private final ResourceStatusRepository statuses;

	/**
	 * Creates the service.
	 *
	 * @param catalogRepositories the repository of every catalog
	 * @param statuses employment statuses
	 * @throws IllegalStateException if a catalog has no repository, so the application fails at start
	 *         instead of on the first request
	 */
	CatalogService(List<CatalogEntityRepository<?, ?>> catalogRepositories, ResourceStatusRepository statuses) {
		catalogRepositories.forEach(repository -> repositories.put(repository.catalog(), repository));
		for (Catalog catalog : Catalog.values()) {
			if (!repositories.containsKey(catalog)) {
				throw new IllegalStateException("No repository for catalog " + catalog);
			}
		}
		this.statuses = statuses;
	}

	/**
	 * The values of a catalog that can be chosen. "No especificado" values are inactive, so they are
	 * never offered.
	 *
	 * @param path name of the catalog in the API path, for example {@code work-modes}
	 * @return its active values
	 * @throws NotFoundException if there is no catalog with that name
	 */
	@Transactional(readOnly = true)
	public ItemsResponse<IdName> list(String path) {
		Catalog catalog = Catalog.fromPath(path).orElseThrow(() -> new NotFoundException(path));
		CatalogEntityRepository<?, ?> repository = repositories.get(catalog);
		List<? extends CatalogEntity> values = catalog.orderedByName() ? repository.findByActiveTrueOrderByNameAsc()
				: repository.findByActiveTrueOrderByIdAsc();
		return new ItemsResponse<>(
				values.stream().map(value -> new IdName(value.getId().longValue(), value.getName())).toList());
	}

	/**
	 * The employment statuses, in their usual order (Activo, Licencia, Baja).
	 *
	 * @return the statuses as code and label
	 */
	@Transactional(readOnly = true)
	public ItemsResponse<CodeLabel> listStatuses() {
		return new ItemsResponse<>(statuses.findByActiveTrueOrderByIdAsc()
			.stream()
			.map(status -> new CodeLabel(status.getCode(), status.getName()))
			.toList());
	}

	/**
	 * Whether a catalog value can be chosen. Runs inside the caller's transaction.
	 *
	 * @param catalog catalog
	 * @param id id of the value
	 * @return {@code true} if active, {@code false} if inactive ("No especificado"), empty if it does
	 *         not exist
	 */
	public Optional<Boolean> findActive(Catalog catalog, Number id) {
		return repositories.get(catalog).findActiveById(id.longValue());
	}

	/**
	 * Id of an active employment status. Runs inside the caller's transaction.
	 *
	 * @param code status code (ACTIVO, LICENCIA, BAJA)
	 * @return its id, or empty if there is no active status with that code
	 */
	public Optional<Short> findStatusId(String code) {
		return statuses.findByCodeAndActiveTrue(code).map(ResourceStatus::getId);
	}

}
