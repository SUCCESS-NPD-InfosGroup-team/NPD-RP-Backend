package com.npdinfosgroup.api.history.dto.response;

import java.time.Instant;

public record LatestChange(String actor, Instant occurredAt) {
}
