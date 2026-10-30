package com.npdinfosgroup.api.history.dto;

import java.time.Instant;
import java.util.List;

public record AuditChange(String actor, Instant occurredAt, String action, List<String> fields) {
}
