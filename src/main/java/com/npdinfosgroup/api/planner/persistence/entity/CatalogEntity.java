package com.npdinfosgroup.api.planner.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@MappedSuperclass
@Getter
public abstract class CatalogEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private boolean active;

	public abstract Number getId();

}
