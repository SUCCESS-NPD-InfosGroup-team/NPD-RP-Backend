package com.npdinfosgroup.api.access.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import com.npdinfosgroup.api.access.client.IdentityProviderUnavailableException;
import com.npdinfosgroup.api.access.service.AccountDeletionProperties;
import com.npdinfosgroup.api.access.service.AccountDeletionService;
import com.npdinfosgroup.api.access.service.AccountDeletionService.PendingDeletion;
import com.npdinfosgroup.api.access.service.AccountStateChanged;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

/**
 * Scheduling the deletion of deactivated accounts: one task per account at its time, replaced or
 * cancelled by later changes, rebuilt on start, and retried when it fails.
 */
class AccountDeletionSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");

	private static final Instant LATER = NOW.plus(Duration.ofHours(3));

	private final TaskScheduler taskScheduler = mock();

	private final AccountDeletionService deletions = mock();

	private final ScheduledFuture<?> future = mock();

	private final AccountDeletionScheduler scheduler = new AccountDeletionScheduler(taskScheduler, deletions,
			new AccountDeletionProperties(Duration.ofDays(1), Duration.ofMinutes(5)), Clock.fixed(NOW, ZoneOffset.UTC));

	/** A committed deactivation schedules the deletion at its time. */
	@Test
	void schedulesTheDeletionOfADeactivatedAccount() {
		givenScheduledTasks();

		scheduler.onStateChanged(new AccountStateChanged(7L, LATER));

		verify(taskScheduler).schedule(any(Runnable.class), eq(LATER));
	}

	/** A committed reactivation cancels the scheduled deletion. */
	@Test
	void cancelsTheDeletionOfAReactivatedAccount() {
		givenScheduledTasks();
		scheduler.onStateChanged(new AccountStateChanged(7L, LATER));

		scheduler.onStateChanged(new AccountStateChanged(7L, null));

		verify(future).cancel(false);
	}

	/** Scheduling an account again replaces its earlier task. */
	@Test
	void replacesAnEarlierTask() {
		givenScheduledTasks();
		scheduler.schedule(7L, LATER);

		scheduler.schedule(7L, LATER.plusSeconds(60));

		verify(future).cancel(false);
	}

	/** Cancelling an account with no task does nothing. */
	@Test
	void cancelsNothingWhenThereIsNoTask() {
		scheduler.cancel(7L);

		verify(future, never()).cancel(false);
	}

	/** On start, every deactivated account is scheduled again, overdue ones included. */
	@Test
	void schedulesTheDeactivatedAccountsOnStart() {
		givenScheduledTasks();
		when(deletions.pending()).thenReturn(List.of(new PendingDeletion(7L, NOW.minusSeconds(30)),
				new PendingDeletion(8L, LATER)));

		scheduler.scheduleDeactivatedAccounts();

		verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.minusSeconds(30)));
		verify(taskScheduler).schedule(any(Runnable.class), eq(LATER));
	}

	/** The task deletes the account when it runs. */
	@Test
	void deletesWhenTheTaskRuns() {
		Runnable task = scheduledTask(7L);

		task.run();

		verify(deletions).deleteIfDue(7L);
	}

	/** A failed deletion is retried after the retry delay. */
	@Test
	void retriesAFailedDeletion() {
		Runnable task = scheduledTask(7L);
		when(deletions.deleteIfDue(7L)).thenThrow(new IdentityProviderUnavailableException(new RuntimeException()));

		task.run();

		verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofMinutes(5))));
	}

	private Runnable scheduledTask(long userId) {
		givenScheduledTasks();
		scheduler.schedule(userId, LATER);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(taskScheduler).schedule(task.capture(), eq(LATER));
		return task.getValue();
	}

	private void givenScheduledTasks() {
		doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
	}

}
