package com.npdinfosgroup.api.planner.dto.request;

import java.util.List;

public record ResourceSearchCriteria(String text, Long clientId, Long projectId, Long leaderId, Long clientRoleId,
		Short level, List<String> statuses, List<String> situations) {
}
