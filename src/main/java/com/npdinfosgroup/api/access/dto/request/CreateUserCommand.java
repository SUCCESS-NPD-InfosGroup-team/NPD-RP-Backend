package com.npdinfosgroup.api.access.dto.request;

import com.npdinfosgroup.api.shared.security.Role;

public record CreateUserCommand(String fullName, String email, Role role, String actor) {
}
