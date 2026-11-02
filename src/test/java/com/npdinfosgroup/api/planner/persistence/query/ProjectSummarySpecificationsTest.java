package com.npdinfosgroup.api.planner.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The LIKE wildcards typed in «Buscar» are matched literally, so "50%" does not match every project
 * that contains "50".
 */
class ProjectSummarySpecificationsTest {

	/** Each wildcard and the escape character itself are escaped; other text is unchanged. */
	@Test
	void escapesLikeWildcards() {
		assertThat(ProjectSummarySpecifications.escapeLike("banca movil")).isEqualTo("banca movil");
		assertThat(ProjectSummarySpecifications.escapeLike("50%")).isEqualTo("50\\%");
		assertThat(ProjectSummarySpecifications.escapeLike("qa_01")).isEqualTo("qa\\_01");
		assertThat(ProjectSummarySpecifications.escapeLike("a\\b")).isEqualTo("a\\\\b");
	}

}
