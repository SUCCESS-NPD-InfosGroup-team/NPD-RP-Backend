package com.npdinfosgroup.api.planner.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

/**
 * Reglas de limpieza de los valores del planificador.
 */
class PlannerValuesTest {

	/**
	 * Los espacios repetidos, los saltos de línea y los marcadores de vacío se limpian.
	 */
	@Test
	void cleanNormalizesSpacesAndEmptyMarkers() {
		assertThat(PlannerValues.clean("  Eyra \n Castro ")).isEqualTo("Eyra Castro");
		assertThat(PlannerValues.clean(null)).isNull();
		assertThat(PlannerValues.clean("   ")).isNull();
		assertThat(PlannerValues.clean("*")).isNull();
		assertThat(PlannerValues.clean(" - ")).isNull();
		assertThat(PlannerValues.clean("n/a")).isNull();
	}

	/**
	 * Los errores de tipeo conocidos se corrigen sin importar mayúsculas.
	 */
	@Test
	void cleanFixesKnownTypos() {
		assertThat(PlannerValues.clean("NPD-ADMNISTRACION")).isEqualTo("NPD-ADMINISTRACION");
		assertThat(PlannerValues.code("Pesencial - L/S")).isEqualTo("PRESENCIAL_L_S");
	}

	/**
	 * Los valores escritos de formas distintas dan el mismo código de catálogo.
	 */
	@Test
	void codeMatchesCatalogCodes() {
		assertThat(PlannerValues.code("Hibrido . 1 al mes")).isEqualTo("HIBRIDO_1_AL_MES");
		assertThat(PlannerValues.code("Hibrido - 1 al mes")).isEqualTo("HIBRIDO_1_AL_MES");
		assertThat(PlannerValues.code("Futuro líder")).isEqualTo("FUTURO_LIDER");
		assertThat(PlannerValues.code("Renuncia Voluntaria")).isEqualTo("RENUNCIA_VOLUNTARIA");
	}

	/**
	 * El nombre corto del líder coincide si todas sus palabras están en el nombre completo.
	 */
	@Test
	void nameMatchesIgnoresAccentsAndMiddleNames() {
		assertThat(PlannerValues.nameMatches("ROGER RODRÍGUEZ", "Roger Rafael Rodriguez Montenegro")).isTrue();
		assertThat(PlannerValues.nameMatches("JORGE RUIZ", "Kathia Bermúdez")).isFalse();
	}

	/**
	 * El formato del correo y su dominio.
	 */
	@Test
	void emailFormatAndDomain() {
		assertThat(PlannerValues.isEmail("ana@cliente.com.pa")).isTrue();
		assertThat(PlannerValues.isEmail("ana@cliente")).isFalse();
		assertThat(PlannerValues.isEmail("ana cliente.com")).isFalse();
		assertThat(PlannerValues.isEmail("@cliente.com")).isFalse();
		assertThat(PlannerValues.isEmail("ana@@cliente.com")).isFalse();
		assertThat(PlannerValues.isEmail("ana@.com")).isFalse();
		assertThat(PlannerValues.isEmail("ana@cliente.")).isFalse();
		assertThat(PlannerValues.isEmail("ana maria@cliente.com")).isFalse();
		assertThat(PlannerValues.domain("Ana@Cliente.COM")).isEqualTo("cliente.com");
	}

	/**
	 * MES FINAL DE PROYECTO se interpreta como mes y año.
	 */
	@Test
	void endMonthParsesSpanishMonths() {
		assertThat(PlannerValues.endMonth("DICIEMBRE 2026")).isEqualTo(YearMonth.of(2026, 12));
		assertThat(PlannerValues.endMonth("setiembre 2027")).isEqualTo(YearMonth.of(2027, 9));
		assertThat(PlannerValues.endMonth("INDEFINIDO")).isNull();
		assertThat(PlannerValues.endMonth("DICIEMBRE")).isNull();
		assertThat(PlannerValues.endMonth("MES 2026")).isNull();
		assertThat(PlannerValues.endMonth("ENERO 26")).isNull();
	}

}
