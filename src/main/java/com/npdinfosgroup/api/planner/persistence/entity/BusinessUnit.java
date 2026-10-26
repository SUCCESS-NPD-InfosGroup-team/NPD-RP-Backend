package com.npdinfosgroup.api.planner.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "business_unit")
@Immutable
@Getter
public class BusinessUnit extends CatalogEntity {

	@Id
	private Short id;

	@Column(nullable = false)
	private String code;

}
