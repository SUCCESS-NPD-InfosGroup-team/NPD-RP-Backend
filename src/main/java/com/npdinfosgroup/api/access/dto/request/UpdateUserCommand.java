package com.npdinfosgroup.api.access.dto.request;

public record UpdateUserCommand(long id, UpdateUserRequest user, String actor) {
}
