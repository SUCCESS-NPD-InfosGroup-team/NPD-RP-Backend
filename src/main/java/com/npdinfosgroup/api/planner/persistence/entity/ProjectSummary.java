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

	@Column(name = "project")
	private String name;

	private boolean active;

	@Column(name = "contract_end_date")
	private LocalDate contractEndDate;

	@Column(name = "team_size")
	private Long teamSize;

}
