package com.npdinfosgroup.api.planner.persistence.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "project_summary_v")
@Immutable
@Getter
public class ProjectSummary {

	@Id
	@Column(name = "project_id")
	private Long id;

	@Column(name = "client_id")
	private Long clientId;

	@Column(name = "client")
	private String clientName;

	@Column(name = "is_internal")
	private boolean clientInternal;

	@Column(name = "project")
	private String name;

	private boolean active;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "contract_end_date")
	private LocalDate contractEndDate;

	@Column(name = "team_size")
	private Long teamSize;

	@Column(name = "pending_confirmation")
	private Long pendingConfirmation;

	@Column(name = "next_assignment_end")
	private LocalDate nextAssignmentEnd;

	@Column(name = "indefinite_count")
	private Long indefiniteCount;

}
