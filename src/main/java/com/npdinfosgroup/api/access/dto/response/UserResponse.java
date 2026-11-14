package com.npdinfosgroup.api.access.dto.response;

import java.time.Instant;

public record UserResponse(Long id, String fullName, String email, String role, String state, Instant lastAccessAt) {
}
