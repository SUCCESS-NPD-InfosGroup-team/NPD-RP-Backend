package com.npdinfosgroup.api.planner.importer;

import java.text.Normalizer;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reglas de limpieza de los valores del planificador.
 * <p>
 * El Excel trae espacios de más, saltos de línea, marcadores de vacío ({@code *}, {@code -},
 * {@code N/A}) y errores de tipeo repetidos. Estas funciones dejan cada valor listo para compararlo
 * con los catálogos o guardarlo.
 */
final class PlannerValues {

	/** Código que se usa en los catálogos cuando el Excel deja vacío un dato obligatorio. */
	static final String NOT_SPECIFIED = "NO_ESPECIFICADO";

	private static final Set<String> EMPTY_MARKERS = Set.of("*", "-", "N/A");

	/** Errores de tipeo del planificador y su corrección. */
	private static final Map<String, String> TYPOS = Map.of(
			"PESENCIAL", "PRESENCIAL",
			"ADMNISTRACION", "ADMINISTRACION");

	private static final List<String> MONTHS = List.of("ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
			"JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE");

	private PlannerValues() {
	}

	/**
	 * Limpia un valor de texto: une los espacios y saltos de línea, quita los de los extremos,
	 * convierte los marcadores de vacío en {@code null} y corrige los errores de tipeo conocidos.
	 *
	 * @param raw valor tal como viene en el Excel
	 * @return el valor limpio, o {@code null} si no tiene datos
	 */
	static String clean(String raw) {
		if (raw == null) {
			return null;
		}
		String value = raw.replaceAll("\\s+", " ").strip();
		if (value.isEmpty() || EMPTY_MARKERS.contains(value.toUpperCase(Locale.ROOT))) {
			return null;
		}
		for (Map.Entry<String, String> typo : TYPOS.entrySet()) {
			value = value.replaceAll("(?i)" + typo.getKey(), typo.getValue());
		}
		return value;
	}

	/**
	 * Texto sin tildes, en mayúsculas y con los espacios unidos, para comparar valores escritos de
	 * formas distintas.
	 *
	 * @param value texto a normalizar
	 * @return el texto normalizado
	 */
	static String key(String value) {
		String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return withoutAccents.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
	}

	/**
	 * Convierte un valor al formato de los códigos de catálogo: "Hibrido . 1 al mes" pasa a
	 * {@code HIBRIDO_1_AL_MES}.
	 *
	 * @param value valor limpio
	 * @return el código equivalente
	 */
	static String code(String value) {
		String code = key(value).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
		for (Map.Entry<String, String> typo : TYPOS.entrySet()) {
			code = code.replace(typo.getKey(), typo.getValue());
		}
		return code;
	}

	/**
	 * Indica si el nombre corto de un líder ("JORGE RUIZ") corresponde al nombre completo de un
	 * empleado ("Jorge Aldayr Ruiz Cruz"): todas sus palabras deben aparecer en el nombre completo.
	 *
	 * @param shortName nombre como aparece en la columna LÍDER TLP
	 * @param fullName nombre completo del empleado
	 * @return {@code true} si todas las palabras del nombre corto están en el nombre completo
	 */
	static boolean nameMatches(String shortName, String fullName) {
		List<String> fullWords = Arrays.asList(key(fullName).split(" "));
		return Arrays.stream(key(shortName).split(" ")).allMatch(fullWords::contains);
	}

	/**
	 * Valida el formato básico de un correo.
	 *
	 * @param email correo ya limpio
	 * @return {@code true} si tiene la forma {@code usuario@dominio.ext}
	 */
	static boolean isEmail(String email) {
		int at = email.indexOf('@');
		int dot = email.lastIndexOf('.');
		return at > 0 && at == email.lastIndexOf('@') && dot > at + 1 && dot < email.length() - 1
				&& email.chars().noneMatch(Character::isWhitespace);
	}

	/**
	 * Dominio de un correo, en minúsculas.
	 *
	 * @param email correo con formato válido
	 * @return lo que sigue a la arroba
	 */
	static String domain(String email) {
		return email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
	}

	/**
	 * Interpreta la columna MES FINAL DE PROYECTO, por ejemplo "DICIEMBRE 2026".
	 *
	 * @param value valor limpio de la columna
	 * @return el mes y año, o {@code null} si el texto no tiene ese formato
	 */
	static YearMonth endMonth(String value) {
		String[] parts = key(value).replace("SETIEMBRE", "SEPTIEMBRE").split(" ");
		int month = MONTHS.indexOf(parts[0]);
		if (parts.length != 2 || month < 0 || !parts[1].matches("\\d{4}")) {
			return null;
		}
		return YearMonth.of(Integer.parseInt(parts[1]), month + 1);
	}

}
