package com.npdinfosgroup.api.planner.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String code;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(length = 254)
	private String email;

	@Column(name = "gender_id", nullable = false)
	private Short genderId;

	@Column(name = "job_title_id")
	private Long jobTitleId;

	@Column(name = "job_position_id", nullable = false)
	private Long jobPositionId;

	private Short level;

	@Column(name = "business_unit_id")
	private Short businessUnitId;

	@Column(name = "work_modality_id")
	private Short workModalityId;

	@Column(name = "hire_date", nullable = false)
	private LocalDate hireDate;

	@Column(name = "probation_end_date", insertable = false, updatable = false)
	private LocalDate probationEndDate;

	@Column(name = "vacation_month", insertable = false, updatable = false)
	private Short vacationMonth;

	@Column(name = "status_id", nullable = false)
	private Short statusId;

	@Column(name = "termination_reason_id")
	private Short terminationReasonId;

	@Column(name = "termination_date")
	private LocalDate terminationDate;

	@Column(name = "talent_review_id")
	private Short talentReviewId;

	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "leader_id")
	private Long leaderId;

	@Column(name = "client_role_id")
	private Long clientRoleId;

	@Column(name = "equipment_provider_id")
	private Short equipmentProviderId;

	@Column(name = "client_contact_email", length = 254)
	private String clientContactEmail;

	@Column(name = "project_end_date")
	private LocalDate projectEndDate;

	@Version
	@Column(nullable = false)
	private Integer version;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

}
