package com.npdinfosgroup.api.planner.dto.request;

public record UpdateProjectCommand(long id, UpdateProjectRequest project, String actor) {
}
