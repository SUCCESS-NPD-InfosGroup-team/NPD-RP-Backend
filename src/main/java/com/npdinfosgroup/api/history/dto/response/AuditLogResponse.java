package com.npdinfosgroup.api.history.dto.response;

import java.util.List;

import com.npdinfosgroup.api.shared.dto.PageInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Auditoría de un recurso: una página de entradas y las opciones de sus filtros")
public record AuditLogResponse(
		@Schema(description = "Entradas de la página, las más recientes primero") List<AuditLogEntryResponse> items,
		PageInfo page,
		@Schema(description = "Usuarios que aparecen en la auditoría del recurso, para el filtro «Usuario»") List<String> actors,
		@Schema(description = "Campos editados alguna vez en el recurso, para el filtro «Campo»") List<AuditFieldOption> fields) {
}
