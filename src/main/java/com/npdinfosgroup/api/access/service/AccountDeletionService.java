package com.npdinfosgroup.api.access.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.client.KeycloakAdminClient;
import com.npdinfosgroup.api.access.persistence.entity.AppUser;
import com.npdinfosgroup.api.access.persistence.entity.AppUserState;
import com.npdinfosgroup.api.access.persistence.repository.AppUserRepository;
import com.npdinfosgroup.api.history.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Deletes a deactivated account when its day has passed (SCRUM-231 CA-08): from Keycloak and from
 * {@code app_user}, keeping its name and email in the audit log. See
 * {@code docs/specs/SCRUM-231/design.md} §3 and §5.
 * <p>
 * The deletion runs with the user locked, so a reactivation saved at the same moment waits and then finds
 * no user, and the deletion never trusts the task that called it: it checks again that the account is
 * still deactivated and due. The employee record of an NPD lead is never deleted (CA-09).
 */
@Service
public class AccountDeletionService {

	private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

	/** Entity name of the users in {@code audit_log}, as their creation records it. */
	private static final String AUDIT_ENTITY = "app_user";

	/** Actor of the deletion: it has no user behind it; who deactivated the account is in that row. */
	static final String SYSTEM_ACTOR = "system";

	private final AppUserRepository users;

	private final KeycloakAdminClient keycloak;

	private final AuditService audit;

	private final TransactionOperations transactions;

	private final Clock clock;

	private final AccountDeletionProperties deletion;

	/**
	 * Creates the service.
	 *
	 * @param users reads, locks and deletes the user
	 * @param keycloak deletes the account
	 * @param audit records the deletion
	 * @param transactions runs the deletion with the user locked
	 * @param clock tells whether the deletion time has passed
	 * @param deletion how long after the deactivation the account is deleted
	 */
	AccountDeletionService(AppUserRepository users, KeycloakAdminClient keycloak, AuditService audit,
			TransactionOperations transactions, Clock clock, AccountDeletionProperties deletion) {
		this.users = users;
		this.keycloak = keycloak;
		this.audit = audit;
		this.transactions = transactions;
		this.clock = clock;
		this.deletion = deletion;
	}

	/**
	 * Every deactivated account with its deletion time, to schedule them again when the API starts.
	 *
	 * @return the accounts, overdue ones included
	 */
	@Transactional(readOnly = true)
	public List<PendingDeletion> pending() {
		return users.findByState(AppUserState.DISABLED)
			.stream()
			.map(user -> new PendingDeletion(user.getId(), deletion.deletesAt(user.getDisabledAt())))
			.toList();
	}

	/**
	 * Deletes the account if it is still deactivated and its deletion time has passed; otherwise (it was
	 * reactivated, deactivated again later, or already deleted) does nothing. Keycloak goes first: if the
	 * database then fails, the account stays {@code DISABLED} and the next attempt finds it already gone in
	 * Keycloak and completes the deletion.
	 *
	 * @param id user id
	 * @return {@code true} if it was deleted now
	 * @throws IdentityProviderUnavailableException if Keycloak cannot be reached; nothing is deleted
	 */
	public boolean deleteIfDue(long id) {
		Boolean deleted = transactions.execute(status -> {
			AppUser user = users.lockById(id).orElse(null);
			if (user == null || user.getState() != AppUserState.DISABLED) {
				return false;
			}
			Instant deletesAt = deletion.deletesAt(user.getDisabledAt());
			if (clock.instant().isBefore(deletesAt)) {
				return false;
			}
			keycloak.deleteAccount(user.getKeycloakId());
			Map<String, String> lastValues = new LinkedHashMap<>();
			lastValues.put("fullName", user.getFullName());
			lastValues.put("email", user.getEmail());
			lastValues.put("role", user.getRole());
			users.delete(user);
			users.flush();
			audit.recordDeletion(AUDIT_ENTITY, id, lastValues, SYSTEM_ACTOR);
			return true;
		});
		if (Boolean.TRUE.equals(deleted)) {
			log.info("User {} deleted one day after its deactivation", id);
		}
		return Boolean.TRUE.equals(deleted);
	}

	/**
	 * A deactivated account and when it is deleted.
	 *
	 * @param userId user id
	 * @param deletesAt deletion time
	 */
	public record PendingDeletion(long userId, Instant deletesAt) {
	}

}
