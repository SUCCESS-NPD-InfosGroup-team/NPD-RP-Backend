package com.npdinfosgroup.api.planner.persistence.repository;

import com.npdinfosgroup.api.planner.persistence.entity.UpcomingEvent;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Dated events of the upcoming events list (HU-06), from the {@code upcoming_event_v} view (read
 * only). The list filters them with {@code UpcomingEventSpecifications}.
 */
public interface UpcomingEventRepository
		extends Repository<UpcomingEvent, String>, JpaSpecificationExecutor<UpcomingEvent> {

}
