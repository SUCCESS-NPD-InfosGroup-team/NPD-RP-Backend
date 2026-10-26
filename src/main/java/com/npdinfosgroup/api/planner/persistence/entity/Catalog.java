package com.npdinfosgroup.api.planner.persistence.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catalogs of the planner whose active values the forms offer, each with its name in the API path
 * ({@code /catalogs/{path}}) and the order of its values.
 */
public enum Catalog {

	JOB_TITLES("job-titles", true),
	POSITIONS("positions", true),
	BUSINESS_UNITS("business-units", false),
	WORK_MODES("work-modes", false),
	GENDERS("genders", false),
	TALENT_REVIEWS("talent-reviews", false),
	CLIENT_ROLES("client-roles", true),
	EQUIPMENT("equipment", false),
	TERMINATION_REASONS("termination-reasons", false);

	private final String path;

	private final boolean orderedByName;

	Catalog(String path, boolean orderedByName) {
		this.path = path;
		this.orderedByName = orderedByName;
	}

	/**
	 * Whether the values go by name. Catalogs that grow with the data (job titles, positions, client
	 * roles) do; the fixed ones keep the order they were seeded in.
	 *
	 * @return {@code true} to order by name, {@code false} by id
	 */
	public boolean orderedByName() {
		return orderedByName;
	}

	/**
	 * The catalog with a path name.
	 *
	 * @param path name in the API path
	 * @return the catalog, or empty if there is none with that name
	 */
	public static Optional<Catalog> fromPath(String path) {
		return Arrays.stream(values()).filter(catalog -> catalog.path.equals(path)).findFirst();
	}

}
