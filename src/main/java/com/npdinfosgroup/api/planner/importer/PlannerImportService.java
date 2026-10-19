package com.npdinfosgroup.api.planner.importer;

import static com.npdinfosgroup.api.planner.importer.PlannerColumn.BUSINESS_UNIT;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.CLIENT;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.CLIENT_CONTACT;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.CLIENT_ROLE;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.EMAIL;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.EMPLOYEE_CODE;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.EQUIPMENT_PROVIDER;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.FULL_NAME;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.GENDER;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.HIRE_DATE;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.JOB_POSITION;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.JOB_TITLE;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.LEADER_CODE;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.LEADER_NAME;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.LEVEL;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.PROJECT;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.PROJECT_END_DATE;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.PROJECT_END_MONTH;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.STATUS;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.TALENT_REVIEW;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.TERMINATION_REASON;
import static com.npdinfosgroup.api.planner.importer.PlannerColumn.WORK_MODALITY;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.npdinfosgroup.api.planner.dto.request.ImportEmployeeCommand;
import com.npdinfosgroup.api.planner.persistence.repository.PlannerImportRepository;
import com.npdinfosgroup.api.planner.persistence.repository.PlannerImportRepository.EmployeeRef;
import com.npdinfosgroup.api.planner.persistence.repository.PlannerImportRepository.FixedCatalog;
import com.npdinfosgroup.api.planner.persistence.repository.PlannerImportRepository.OpenCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports the planner rows into the database.
 * <p>
 * Rules agreed for the initial import (story SCRUM-66):
 * <ul>
 * <li>A row is rejected if it lacks a value the database requires (code, name, position, hire
 * date, gender, status), if its code already appeared in another row or if a required value is
 * unknown.</li>
 * <li>Empty work modality, client and project are saved as "No especificado" and listed in the
 * report so they get filled in later.</li>
 * <li>The leader is assigned by COD LIDER. If the code is not an employee, the row has no
 * leader; if the LIDER TLP name does not match, the code wins.</li>
 * <li>Everything else is imported as is and flagged in the report when it needs a review.</li>
 * </ul>
 * The whole import runs in one transaction: if anything fails, nothing is saved. Running it again
 * updates employees by code instead of duplicating them.
 */
@Service
class PlannerImportService {

	static final String INTERNAL_CLIENT = "NPD-ADMINISTRACION";

	private static final String NO_PROJECT = "SIN_PROYECTO";

	private static final String INDEFINITE = "INDEFINIDO";

	private static final String ACTIVE = "ACTIVO";

	private final PlannerImportRepository repository;

	/**
	 * Creates the service.
	 *
	 * @param repository import data access
	 */
	PlannerImportService(PlannerImportRepository repository) {
		this.repository = repository;
	}

	/**
	 * Imports the rows: creates or updates employees, creates the missing clients, projects and
	 * catalog values, and assigns leaders.
	 *
	 * @param rows rows read from the Excel
	 * @param today import date, to flag projects whose end date has passed
	 * @return how many rows were imported and what needs a review
	 */
	@Transactional
	public PlannerImportReport importRows(List<PlannerRow> rows, LocalDate today) {
		ImportRun run = new ImportRun(loadCatalogs(), contactDomainOwners(rows), today);
		List<PendingLeader> leaders = new ArrayList<>();
		for (PlannerRow row : rows) {
			ImportEmployeeCommand command = toCommand(row, run);
			if (command != null) {
				leaders.add(new PendingLeader(row, repository.upsertEmployee(command)));
			}
		}
		assignLeaders(leaders, run);
		Comparator<ImportIssue> byRow = Comparator.comparingInt(ImportIssue::rowNumber);
		return new PlannerImportReport(rows.size(), leaders.size(), run.rejected.stream().sorted(byRow).toList(),
				run.flagged.stream().sorted(byRow).toList());
	}

	private Map<FixedCatalog, Map<String, Short>> loadCatalogs() {
		Map<FixedCatalog, Map<String, Short>> catalogs = new EnumMap<>(FixedCatalog.class);
		for (FixedCatalog catalog : FixedCatalog.values()) {
			Map<String, Short> ids = new HashMap<>();
			for (PlannerImportRepository.CatalogValue value : repository.findAll(catalog)) {
				ids.put(PlannerValues.code(value.code()), value.id());
				ids.putIfAbsent(PlannerValues.code(value.name()), value.id());
			}
			catalogs.put(catalog, ids);
		}
		return catalogs;
	}

	/**
	 * "Owner" client of each CONTACTO POR CLIENTE email domain: the client where it is used the
	 * most. Used to flag contacts from another company. Domains owned by the internal client
	 * (Infosgroup addresses) are not flagged.
	 */
	private static Map<String, String> contactDomainOwners(List<PlannerRow> rows) {
		Map<String, Map<String, Long>> clientsByDomain = rows.stream()
			.filter(row -> row.get(CLIENT) != null && !isNoProject(row.get(CLIENT)) && validEmail(row.get(CLIENT_CONTACT)))
			.collect(Collectors.groupingBy(row -> PlannerValues.domain(row.get(CLIENT_CONTACT)),
					Collectors.groupingBy(row -> PlannerValues.key(row.get(CLIENT)), Collectors.counting())));
		Map<String, String> owners = new HashMap<>();
		clientsByDomain.forEach((domain, clients) -> {
			String owner = clients.entrySet()
				.stream()
				.max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
				.orElseThrow()
				.getKey();
			if (!owner.equals(INTERNAL_CLIENT)) {
				owners.put(domain, owner);
			}
		});
		return owners;
	}

	private ImportEmployeeCommand toCommand(PlannerRow row, ImportRun run) {
		RowIssues issues = new RowIssues(row, run);
		String code = row.get(EMPLOYEE_CODE);
		String fullName = row.get(FULL_NAME);
		String statusCode = row.get(STATUS) == null ? null : PlannerValues.code(row.get(STATUS).split("-")[0]);
		for (PlannerColumn column : List.of(EMPLOYEE_CODE, FULL_NAME, JOB_POSITION, GENDER, STATUS)) {
			if (row.get(column) == null) {
				issues.reject(column, "planner.import.rejected.missing");
			}
		}
		if (row.hireDate() == null) {
			issues.reject(HIRE_DATE, "planner.import.rejected.missing");
		}
		if (code != null && run.rowByCode.containsKey(code)) {
			issues.reject(EMPLOYEE_CODE, "planner.import.rejected.duplicate-code", run.rowByCode.get(code));
		}
		Short genderId = requiredFixed(row, GENDER, FixedCatalog.GENDER, run, issues);
		Short statusId = statusCode == null ? null : run.catalogs.get(FixedCatalog.RESOURCE_STATUS).get(statusCode);
		if (statusCode != null && statusId == null) {
			issues.reject(STATUS, "planner.import.rejected.unknown");
		}
		Short modalityId = workModality(row, run, issues);
		if (issues.rejected) {
			return null;
		}
		run.rowByCode.put(code, String.valueOf(row.rowNumber()));

		Short terminationId = optionalFixed(row, TERMINATION_REASON, FixedCatalog.TERMINATION_REASON, run, issues);
		if (terminationId != null && ACTIVE.equals(statusCode)) {
			issues.flag(TERMINATION_REASON, "planner.import.flag.termination-while-active");
		}
		Long projectId = project(row, issues);
		ImportEmployeeCommand command = new ImportEmployeeCommand(code, fullName, email(row, run, issues), genderId,
				openCatalog(row, JOB_TITLE, OpenCatalog.JOB_TITLE), repository.findOrCreate(OpenCatalog.JOB_POSITION,
						row.get(JOB_POSITION)),
				level(row, issues), optionalFixed(row, BUSINESS_UNIT, FixedCatalog.BUSINESS_UNIT, run, issues),
				modalityId, row.hireDate(), statusId, terminationId,
				optionalFixed(row, TALENT_REVIEW, FixedCatalog.TALENT_REVIEW, run, issues), projectId,
				openCatalog(row, CLIENT_ROLE, OpenCatalog.CLIENT_ROLE),
				optionalFixed(row, EQUIPMENT_PROVIDER, FixedCatalog.EQUIPMENT_PROVIDER, run, issues),
				clientContact(row, run, issues), projectEndDate(row, projectId != null, run, issues));
		issues.keepFlags();
		return command;
	}

	private static Short requiredFixed(PlannerRow row, PlannerColumn column, FixedCatalog catalog, ImportRun run,
			RowIssues issues) {
		String value = row.get(column);
		Short id = value == null ? null : run.catalogs.get(catalog).get(PlannerValues.code(value));
		if (value != null && id == null) {
			issues.reject(column, "planner.import.rejected.unknown");
		}
		return id;
	}

	private static Short optionalFixed(PlannerRow row, PlannerColumn column, FixedCatalog catalog, ImportRun run,
			RowIssues issues) {
		String value = row.get(column);
		Short id = value == null ? null : run.catalogs.get(catalog).get(PlannerValues.code(value));
		if (value != null && id == null) {
			issues.flag(column, "planner.import.flag.unknown");
		}
		return id;
	}

	private static Short workModality(PlannerRow row, ImportRun run, RowIssues issues) {
		if (row.get(WORK_MODALITY) == null) {
			issues.flag(WORK_MODALITY, "planner.import.flag.not-specified");
			return run.catalogs.get(FixedCatalog.WORK_MODALITY).get(PlannerValues.NOT_SPECIFIED);
		}
		return requiredFixed(row, WORK_MODALITY, FixedCatalog.WORK_MODALITY, run, issues);
	}

	private Long openCatalog(PlannerRow row, PlannerColumn column, OpenCatalog catalog) {
		String value = row.get(column);
		return value == null ? null : repository.findOrCreate(catalog, value);
	}

	/** Project of the row; {@code null} for "SIN PROYECTO". */
	private Long project(PlannerRow row, RowIssues issues) {
		String client = row.get(CLIENT);
		String project = row.get(PROJECT);
		if ((client != null && isNoProject(client)) || (project != null && isNoProject(project))) {
			return null;
		}
		if (client == null || project == null) {
			if (client == null) {
				issues.flag(CLIENT, "planner.import.flag.not-specified");
			}
			if (project == null) {
				issues.flag(PROJECT, "planner.import.flag.not-specified");
			}
			return repository.findNotSpecifiedProject();
		}
		long clientId = repository.findOrCreateClient(client, PlannerValues.key(client).equals(INTERNAL_CLIENT));
		return repository.findOrCreateProject(clientId, project);
	}

	private static LocalDate projectEndDate(PlannerRow row, boolean hasProject, ImportRun run, RowIssues issues) {
		LocalDate endDate = row.projectEndDate();
		String endText = row.get(PROJECT_END_DATE);
		if (!hasProject) {
			if (endText != null) {
				issues.flag(PROJECT_END_DATE, "planner.import.flag.no-project-end-date");
			}
			return null;
		}
		if (endDate == null && endText != null && !PlannerValues.code(endText).equals(INDEFINITE)) {
			issues.flag(PROJECT_END_DATE, "planner.import.flag.unknown");
		}
		String monthText = row.get(PROJECT_END_MONTH);
		if (monthText != null && !endMonthMatches(endDate, monthText)) {
			issues.flag(PROJECT_END_MONTH, "planner.import.flag.end-month-mismatch",
					endDate == null ? INDEFINITE : endDate.toString());
		}
		if (endDate != null && endDate.isBefore(run.today)) {
			issues.flag(PROJECT_END_DATE, "planner.import.flag.expired");
		}
		return endDate;
	}

	private static boolean endMonthMatches(LocalDate endDate, String monthText) {
		if (endDate == null) {
			return PlannerValues.code(monthText).equals(INDEFINITE);
		}
		return YearMonth.from(endDate).equals(PlannerValues.endMonth(monthText));
	}

	private static String email(PlannerRow row, ImportRun run, RowIssues issues) {
		String email = row.get(EMAIL) == null ? null : row.get(EMAIL).toLowerCase(Locale.ROOT);
		if (email == null) {
			return null;
		}
		if (!PlannerValues.isEmail(email)) {
			issues.flag(EMAIL, "planner.import.flag.invalid-email");
			return null;
		}
		String firstRow = run.rowByEmail.putIfAbsent(email, String.valueOf(row.rowNumber()));
		if (firstRow != null) {
			issues.flag(EMAIL, "planner.import.flag.duplicate-email", firstRow);
			return null;
		}
		return email;
	}

	private static String clientContact(PlannerRow row, ImportRun run, RowIssues issues) {
		String contact = row.get(CLIENT_CONTACT) == null ? null : row.get(CLIENT_CONTACT).toLowerCase(Locale.ROOT);
		if (contact == null) {
			return null;
		}
		if (!PlannerValues.isEmail(contact)) {
			issues.flag(CLIENT_CONTACT, "planner.import.flag.invalid-email");
			return null;
		}
		String owner = run.domainOwners.get(PlannerValues.domain(contact));
		String client = row.get(CLIENT);
		if (owner != null && client != null && !owner.equals(PlannerValues.key(client))) {
			issues.flag(CLIENT_CONTACT, "planner.import.flag.contact-other-client", owner);
		}
		return contact;
	}

	private static Short level(PlannerRow row, RowIssues issues) {
		String value = row.get(LEVEL);
		if (value == null) {
			return null;
		}
		if (value.matches("\\d{1,4}") && Short.parseShort(value) > 0) {
			return Short.valueOf(value);
		}
		issues.flag(LEVEL, "planner.import.flag.unknown");
		return null;
	}

	/**
	 * Assigns leaders once every employee exists, because a leader can appear in a row after the
	 * people they lead.
	 */
	private void assignLeaders(List<PendingLeader> leaders, ImportRun run) {
		Map<String, EmployeeRef> employees = repository.findEmployeesByCode();
		for (PendingLeader pending : leaders) {
			PlannerRow row = pending.row();
			RowIssues issues = new RowIssues(row, run);
			String leaderCode = row.get(LEADER_CODE);
			EmployeeRef leader = leaderCode == null ? null : employees.get(leaderCode);
			if (leaderCode == null) {
				issues.flag(LEADER_CODE, "planner.import.flag.leader-missing");
			}
			else if (leader == null) {
				issues.flag(LEADER_CODE, "planner.import.flag.leader-not-found", String.valueOf(row.get(LEADER_NAME)));
			}
			else if (leader.id() == pending.employeeId()) {
				issues.flag(LEADER_CODE, "planner.import.flag.leader-self");
				leader = null;
			}
			else if (row.get(LEADER_NAME) != null && !PlannerValues.nameMatches(row.get(LEADER_NAME), leader.fullName())) {
				issues.flag(LEADER_NAME, "planner.import.flag.leader-name-mismatch", leader.fullName());
			}
			issues.keepFlags();
			repository.updateLeader(pending.employeeId(), leader == null ? null : leader.id());
		}
	}

	private static boolean isNoProject(String value) {
		return PlannerValues.code(value).equals(NO_PROJECT);
	}

	private static boolean validEmail(String value) {
		return value != null && PlannerValues.isEmail(value);
	}

	/** State of one import run: loaded catalogs and what it finds along the way. */
	private static final class ImportRun {

		private final Map<FixedCatalog, Map<String, Short>> catalogs;

		private final Map<String, String> domainOwners;

		private final LocalDate today;

		private final Map<String, String> rowByCode = new HashMap<>();

		private final Map<String, String> rowByEmail = new HashMap<>();

		private final List<ImportIssue> rejected = new ArrayList<>();

		private final List<ImportIssue> flagged = new ArrayList<>();

		private ImportRun(Map<FixedCatalog, Map<String, Short>> catalogs, Map<String, String> domainOwners,
				LocalDate today) {
			this.catalogs = catalogs;
			this.domainOwners = domainOwners;
			this.today = today;
		}

	}

	/**
	 * Collects the issues of one row. Rejections go straight to the run; flags only when the row is
	 * imported ({@link #keepFlags()}), because flags on a rejected row are of no use.
	 */
	private static final class RowIssues {

		private final PlannerRow row;

		private final ImportRun run;

		private final List<ImportIssue> flags = new ArrayList<>();

		private boolean rejected;

		private RowIssues(PlannerRow row, ImportRun run) {
			this.row = row;
			this.run = run;
		}

		private void reject(PlannerColumn column, String messageKey, String... arguments) {
			rejected = true;
			run.rejected.add(issue(column, messageKey, arguments));
		}

		private void flag(PlannerColumn column, String messageKey, String... arguments) {
			flags.add(issue(column, messageKey, arguments));
		}

		private void keepFlags() {
			run.flagged.addAll(flags);
		}

		private ImportIssue issue(PlannerColumn column, String messageKey, String... arguments) {
			return new ImportIssue(row.rowNumber(), row.get(EMPLOYEE_CODE), row.get(FULL_NAME), column,
					row.raw(column), messageKey, List.of(arguments));
		}

	}

	/** Saved employee waiting for its leader. */
	private record PendingLeader(PlannerRow row, long employeeId) {
	}

}
