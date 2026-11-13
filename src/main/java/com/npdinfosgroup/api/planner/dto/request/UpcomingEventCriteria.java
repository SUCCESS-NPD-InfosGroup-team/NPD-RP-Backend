package com.npdinfosgroup.api.planner.dto.request;

public record UpcomingEventCriteria(int windowDays, Long clientId, Long leaderId, UpcomingEventType type) {
}
