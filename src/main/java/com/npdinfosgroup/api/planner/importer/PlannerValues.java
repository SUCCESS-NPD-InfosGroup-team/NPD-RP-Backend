package com.npdinfosgroup.api.planner.importer;

import java.text.Normalizer;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cleaning rules for the planner values.
 * <p>
 * The Excel has extra spaces, line breaks, empty markers ({@code *}, {@code -},
 * {@code N/A}) and repeated typos. These functions leave each value ready to compare with the
 * catalogs or to store.
 */
final class PlannerValues {

	/** Catalog code used when the Excel leaves a required value empty. */
	static final String NOT_SPECIFIED = "NO_ESPECIFICADO";

	private static final Set<String> EMPTY_MARKERS = Set.of("*", "-", "N/A");

	/** Typos in the planner and their fix. */
	private static final Map<String, String> TYPOS = Map.of(
			"PESENCIAL", "PRESENCIAL",
			"ADMNISTRACION", "ADMINISTRACION");

	private static final List<String> MONTHS = List.of("ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
			"JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE");

	private PlannerValues() {
	}

	/**
	 * Cleans a text value: joins spaces and line breaks, trims it, turns empty markers into
	 * {@code null} and fixes known typos.
	 *
	 * @param raw value as it comes in the Excel
	 * @return the clean value, or {@code null} if it has no data
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
	 * Text without accents, upper case and with joined spaces, to compare values written in
	 * different ways.
	 *
	 * @param value text to normalize
	 * @return the normalized text
	 */
	static String key(String value) {
		String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return withoutAccents.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
	}

	/**
	 * Turns a value into the catalog code format: "Hibrido . 1 al mes" becomes
	 * {@code HIBRIDO_1_AL_MES}.
	 *
	 * @param value clean value
	 * @return the equivalent code
	 */
	static String code(String value) {
		String code = key(value).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
		for (Map.Entry<String, String> typo : TYPOS.entrySet()) {
			code = code.replace(typo.getKey(), typo.getValue());
		}
		return code;
	}

	/**
	 * Tells whether a leader's short name ("JORGE RUIZ") matches an employee's full name
	 * ("Jorge Aldayr Ruiz Cruz"): every word of it must appear in the full name.
	 *
	 * @param shortName name as it appears in the LIDER TLP column
	 * @param fullName employee's full name
	 * @return {@code true} if every word of the short name is in the full name
	 */
	static boolean nameMatches(String shortName, String fullName) {
		List<String> fullWords = Arrays.asList(key(fullName).split(" "));
		return Arrays.stream(key(shortName).split(" ")).allMatch(fullWords::contains);
	}

	/**
	 * Checks the basic format of an email address.
	 *
	 * @param email clean email address
	 * @return {@code true} if it has the form {@code user@domain.ext}
	 */
	static boolean isEmail(String email) {
		int at = email.indexOf('@');
		int dot = email.lastIndexOf('.');
		return at > 0 && at == email.lastIndexOf('@') && dot > at + 1 && dot < email.length() - 1
				&& email.chars().noneMatch(Character::isWhitespace);
	}

	/**
	 * Domain of an email address, in lower case.
	 *
	 * @param email email address with a valid format
	 * @return what follows the at sign
	 */
	static String domain(String email) {
		return email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
	}

	/**
	 * Parses the MES FINAL DE PROYECTO column, for example "DICIEMBRE 2026".
	 *
	 * @param value clean value of the column
	 * @return the month and year, or {@code null} if the text does not have that format
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
