package com.npdinfosgroup.api.history.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "entity_type", nullable = false, length = 40)
	private String entityType;

	@Column(name = "entity_id", nullable = false)
	private Long entityId;

	@Column(nullable = false, length = 20)
	private String action;

	@Column(nullable = false, length = 100)
	private String field;

	@Column(name = "old_value", columnDefinition = "text")
	private String oldValue;

	@Column(name = "new_value", columnDefinition = "text")
	private String newValue;

	@Column(nullable = false, length = 150)
	private String actor;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

}
