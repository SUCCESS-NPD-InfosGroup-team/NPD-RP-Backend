package com.npdinfosgroup.api.planner.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.npdinfosgroup.api.planner.persistence.repository.Catalog;
import com.npdinfosgroup.api.planner.persistence.repository.GenderRepository;
import com.npdinfosgroup.api.planner.persistence.repository.ResourceStatusRepository;
import org.junit.jupiter.api.Test;

/**
 * A catalog without repository is a programming error that must stop the application at start.
 */
class CatalogServiceTest {

	/** Only the genders repository is given, so the other catalogs have none. */
	@Test
	void failsWhenACatalogHasNoRepository() {
		GenderRepository genders = mock();
		when(genders.catalog()).thenReturn(Catalog.GENDERS);

		assertThatThrownBy(() -> new CatalogService(List.of(genders), mock(ResourceStatusRepository.class)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("No repository for catalog ");
	}

}
