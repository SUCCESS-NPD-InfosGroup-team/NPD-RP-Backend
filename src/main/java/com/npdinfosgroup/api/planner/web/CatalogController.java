package com.npdinfosgroup.api.planner.web;

import com.npdinfosgroup.api.planner.dto.response.CodeLabel;
import com.npdinfosgroup.api.planner.dto.response.IdName;
import com.npdinfosgroup.api.planner.dto.response.ItemsResponse;
import com.npdinfosgroup.api.planner.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Options of the form selectors, one catalog per call.
 */
@RestController
@RequestMapping("/api/{version}/catalogs")
@Tag(name = "Catálogos", description = "Opciones de los selectores de los formularios")
class CatalogController {

	private static final String PROBLEM_JSON = "application/problem+json";

	private final CatalogService catalogService;

	/**
	 * Creates the controller.
	 *
	 * @param catalogService reads the catalogs
	 */
	CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	/**
	 * The values of one catalog that can be chosen.
	 *
	 * @param catalog name of the catalog in the path
	 * @return 200 with the values
	 */
	@GetMapping(path = "/{catalog}", version = "v1")
	@Operation(summary = "Valores de un catálogo", description = """
			Valores activos de un catálogo, como `{ id, name }`; los «No especificado» nunca se devuelven. \
			Puesto, posición y rol en cliente van por nombre; el resto en su orden fijo. Todos los roles.""")
	@ApiResponse(responseCode = "200", description = "Los valores")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe ese catálogo (`NOT_FOUND`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ItemsResponse<IdName>> list(@Parameter(description = "Catálogo",
			schema = @Schema(allowableValues = { "job-titles", "positions", "business-units", "work-modes", "genders",
					"talent-reviews", "client-roles", "equipment", "termination-reasons" })) @PathVariable String catalog) {
		return ResponseEntity.ok(catalogService.list(catalog));
	}

	/**
	 * The employment statuses. Apart from the other catalogs because they are sent and returned by
	 * code.
	 *
	 * @return 200 with the statuses
	 */
	@GetMapping(path = "/statuses", version = "v1")
	@Operation(summary = "Estados laborales", description = """
			Activo, Licencia y Baja como `{ code, label }`; el registro envía el `code`. Todos los roles.""")
	@ApiResponse(responseCode = "200", description = "Los estados")
	@ApiResponse(responseCode = "401", description = "Sin token o token inválido (`UNAUTHORIZED`)",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<ItemsResponse<CodeLabel>> listStatuses() {
		return ResponseEntity.ok(catalogService.listStatuses());
	}

}
