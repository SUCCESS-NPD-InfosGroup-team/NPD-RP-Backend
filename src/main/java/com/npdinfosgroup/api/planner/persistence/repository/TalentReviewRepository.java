package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.Catalog;
import com.npdinfosgroup.api.planner.persistence.entity.TalentReview;

/**
 * Talent review results.
 */
public interface TalentReviewRepository extends CatalogEntityRepository<TalentReview, Short> {

	@Override
	default Catalog catalog() {
		return Catalog.TALENT_REVIEWS;
	}

}
