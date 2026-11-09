package com.npdinfosgroup.api.shared.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, PageInfo page) {
}
