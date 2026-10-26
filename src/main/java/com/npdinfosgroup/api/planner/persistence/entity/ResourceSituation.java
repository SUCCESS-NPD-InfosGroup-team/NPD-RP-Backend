package com.npdinfosgroup.api.planner.persistence.entity;

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
public class ResourceSituation {

	@Id
	@Column(name = "employee_id")
	private Long employeeId;

	@Column(name = "assignment_situation", columnDefinition = "text")
	private String situation;

}
