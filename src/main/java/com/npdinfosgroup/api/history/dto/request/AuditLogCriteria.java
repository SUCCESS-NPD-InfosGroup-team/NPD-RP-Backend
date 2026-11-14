package com.npdinfosgroup.api.history.dto.request;

import java.time.LocalDate;

public record AuditLogCriteria(String actor, String field, LocalDate from, LocalDate to) {
}
