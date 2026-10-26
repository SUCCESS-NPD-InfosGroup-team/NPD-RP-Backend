package com.npdinfosgroup.api.planner.dto.response;

import java.util.List;

public record ItemsResponse<T>(List<T> items) {
}
