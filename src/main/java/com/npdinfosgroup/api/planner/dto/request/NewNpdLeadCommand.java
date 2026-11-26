package com.npdinfosgroup.api.planner.dto.request;

public record NewNpdLeadCommand(NpdLeadEmployeeRequest employee, String fullName, String email, String actor) {
}
