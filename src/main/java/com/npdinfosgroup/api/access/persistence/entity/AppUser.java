package com.npdinfosgroup.api.access.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "keycloak_id", nullable = false)
	private UUID keycloakId;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(nullable = false, length = 40)
	private String role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AppUserState state;

	@Column(name = "last_access_at")
	private Instant lastAccessAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "created_by", nullable = false, length = 150)
	private String createdBy;

}
