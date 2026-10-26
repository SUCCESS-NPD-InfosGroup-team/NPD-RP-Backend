package com.npdinfosgroup.api.planner.dto.response;

import java.util.List;

public record PageResponse<T>(List<T> items, PageInfo page) {
}
