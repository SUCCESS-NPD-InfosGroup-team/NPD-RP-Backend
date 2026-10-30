package com.npdinfosgroup.api.planner.dto.request;

public record UpdateResourceCommand(long id, UpdateResourceRequest resource, String actor) {
}
