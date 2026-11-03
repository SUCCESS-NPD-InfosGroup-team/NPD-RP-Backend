package com.npdinfosgroup.api.access.dto.request;

public record CreateUserCommand(CreateUserRequest user, String actor) {
}
