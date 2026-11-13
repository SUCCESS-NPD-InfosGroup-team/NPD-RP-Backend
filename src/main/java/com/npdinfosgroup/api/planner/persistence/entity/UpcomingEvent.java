package com.npdinfosgroup.api.planner.persistence.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "upcoming_event_v")
@Immutable
@Getter
public class UpcomingEvent {

	@Id
	@Column(name = "event_id")
	private String id;

	private String kind;

	@Column(name = "event_date")
	private LocalDate date;

	@Column(name = "employee_id")
	private Long employeeId;

	@Column(name = "employee_code")
	private String employeeCode;

	@Column(name = "full_name")
	private String fullName;

	@Column(name = "client_role")
	private String clientRole;

	@Column(name = "resource_count")
	private Long resourceCount;

	@Column(name = "client_id")
	private Long clientId;

	@Column(name = "client")
	private String clientName;

	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "project")
	private String projectName;

	@Column(name = "leader_id")
	private Long leaderId;

	@Column(name = "leader")
	private String leaderName;

}
