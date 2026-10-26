package com.npdinfosgroup.api.planner.persistence.query;

/**
 * An NPD lead with collaborators on a project.
 *
 * @param projectId project id
 * @param leaderId employee id of the lead
 * @param leaderName name of the lead
 */
public record ProjectLead(Long projectId, Long leaderId, String leaderName) {
}
