package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.RosterEntry;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Rows of the roster, from the {@code resource_v} view (read only), filtered with
 * {@link com.npdinfosgroup.api.planner.persistence.query.RosterEntrySpecifications}.
 */
public interface RosterEntryRepository extends Repository<RosterEntry, Long>, JpaSpecificationExecutor<RosterEntry> {

}
