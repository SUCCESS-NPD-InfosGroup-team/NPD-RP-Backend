package com.npdinfosgroup.api.planner.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "talent_review")
@Immutable
@Getter
public class TalentReview extends CatalogEntity {

	@Id
	private Short id;

	@Column(nullable = false)
	private String code;

}
