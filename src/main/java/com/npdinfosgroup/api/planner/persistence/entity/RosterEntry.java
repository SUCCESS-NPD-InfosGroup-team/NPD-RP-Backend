package com.npdinfosgroup.api.planner.persistence.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "resource_v")
@Immutable
@Getter
public class RosterEntry {

	@Id
	@Column(name = "employee_id")
	private Long id;

	@Column(name = "employee_code")
	private String employeeCode;

	@Column(name = "full_name")
	private String fullName;

	private Short level;

	@Column(name = "client_role_id")
	private Long clientRoleId;

	@Column(name = "client_role")
	private String clientRole;

	@Column(name = "leader_id")
	private Long leaderId;

	@Column(name = "npd_leader")
	private String leaderName;

	@Column(name = "employment_status_code")
	private String statusCode;

	@Column(name = "employment_status")
	private String statusName;

	@Column(name = "assignment_situation", columnDefinition = "text")
	private String situation;

	@Column(name = "hire_date")
	private LocalDate hireDate;

	@Column(name = "on_probation")
	private boolean onProbation;

	@Column(name = "probation_end_date")
	private LocalDate probationEndDate;

	@Column(name = "client_id")
	private Long clientId;

	@Column(name = "client")
	private String clientName;

	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "project")
	private String projectName;

	@Column(name = "project_active")
	private Boolean projectActive;

	@Column(name = "contract_end_date")
	private LocalDate contractEndDate;

	@Column(name = "assignment_start_date")
	private LocalDate assignmentStartDate;

	@Column(name = "project_end_date")
	private LocalDate projectEndDate;

	@Column(name = "roster_group")
	private Integer rosterGroup;

	@Column(name = "search_text", columnDefinition = "text")
	private String searchText;

}
