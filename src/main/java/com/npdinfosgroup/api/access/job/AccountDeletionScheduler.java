package com.npdinfosgroup.api.access.job;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import com.npdinfosgroup.api.access.service.AccountDeletionProperties;
import com.npdinfosgroup.api.access.service.AccountDeletionService;
import com.npdinfosgroup.api.access.service.AccountDeletionService.PendingDeletion;
import com.npdinfosgroup.api.access.service.AccountStateChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes each deactivated account at its exact deletion time (SCRUM-231 CA-08): one task per account,
 * scheduled when its deactivation commits and cancelled when its reactivation commits. See
 * {@code docs/specs/SCRUM-231/design.md} §3.
 * <p>
 * The tasks live in memory, so the database is the source of truth: when the API starts, every
 * deactivated account is scheduled again, and an overdue one runs at once. A deletion that fails (Keycloak
 * down) is retried after the configured delay.
 */
@Component
class AccountDeletionScheduler {

	private static final Logger log = LoggerFactory.getLogger(AccountDeletionScheduler.class);

	private final TaskScheduler scheduler;

	private final AccountDeletionService deletions;

	private final AccountDeletionProperties settings;

	private final Clock clock;

	/** The scheduled deletion of each account, by user id. */
	private final Map<Long, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

	/**
	 * Creates the scheduler.
	 *
	 * @param scheduler runs each deletion at its time
	 * @param deletions finds the deactivated accounts and deletes them
	 * @param settings the wait before retrying a failed deletion
	 * @param clock gives the time of a retry
	 */
	AccountDeletionScheduler(TaskScheduler scheduler, AccountDeletionService deletions,
			AccountDeletionProperties settings, Clock clock) {
		this.scheduler = scheduler;
		this.deletions = deletions;
		this.settings = settings;
		this.clock = clock;
	}

	/** Schedules again the deletion of every deactivated account when the API starts. */
	@EventListener(ApplicationReadyEvent.class)
	void scheduleDeactivatedAccounts() {
		for (PendingDeletion pending : deletions.pending()) {
			schedule(pending.userId(), pending.deletesAt());
		}
	}

	/**
	 * Schedules the deletion of a deactivated account, or cancels it for a reactivated one, once the
	 * change is committed.
	 *
	 * @param change the account and its deletion time ({@code null} if it was reactivated)
	 */
	@TransactionalEventListener
	void onStateChanged(AccountStateChanged change) {
		if (change.deletesAt() == null) {
			cancel(change.userId());
		}
		else {
			schedule(change.userId(), change.deletesAt());
		}
	}

	/**
	 * Schedules the deletion of an account at a time, replacing any earlier task of the same account.
	 *
	 * @param userId user id
	 * @param at when to delete it; a past time runs at once
	 */
	void schedule(long userId, Instant at) {
		tasks.compute(userId, (id, earlier) -> {
			if (earlier != null) {
				earlier.cancel(false);
			}
			return scheduler.schedule(() -> run(userId), at);
		});
	}

	/**
	 * Cancels the scheduled deletion of an account, if any.
	 *
	 * @param userId user id
	 */
	void cancel(long userId) {
		ScheduledFuture<?> task = tasks.remove(userId);
		if (task != null) {
			task.cancel(false);
		}
	}

	/** Deletes the account if it is still due; a failure is retried later, not lost. */
	private void run(long userId) {
		try {
			deletions.deleteIfDue(userId);
			tasks.remove(userId);
		}
		catch (RuntimeException ex) {
			log.warn("Deletion of user {} failed, retrying in {}: {}", userId, settings.accountDeletionRetryDelay(),
					ex.getClass().getName());
			schedule(userId, clock.instant().plus(settings.accountDeletionRetryDelay()));
		}
	}

}
