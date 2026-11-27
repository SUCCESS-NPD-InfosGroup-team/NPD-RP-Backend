package com.npdinfosgroup.api.access.dto.request;

public record UserDeletionCommand(long id, Integer version, String actor) {
}
