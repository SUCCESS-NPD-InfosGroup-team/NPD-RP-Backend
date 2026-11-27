package com.npdinfosgroup.api.access.service;

import java.time.Instant;

/**
 * An account was deactivated or reactivated. Published inside the transaction that saved it, so the
 * deletion is scheduled or cancelled only once that transaction commits.
 *
 * @param userId user id
 * @param deletesAt when the account is deleted; {@code null} after a reactivation (nothing to delete)
 */
public record AccountStateChanged(long userId, Instant deletesAt) {

}
