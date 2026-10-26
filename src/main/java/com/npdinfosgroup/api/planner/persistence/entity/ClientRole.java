package com.npdinfosgroup.api.planner.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "client_role")
@Immutable
@Getter
public class ClientRole extends CatalogEntity {

	@Id
	private Long id;

}
