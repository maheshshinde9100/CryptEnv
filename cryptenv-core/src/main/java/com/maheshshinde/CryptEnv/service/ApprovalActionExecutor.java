package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.model.ApprovalRequest;

/**
 * Executes the deferred action of an approved {@link ApprovalRequest} (§10).
 * <p>
 * Implementations are discovered by Spring and selected by {@link #action()}. They run in the
 * approver's security context inside a fresh transaction, so a failing action rolls back on its
 * own without poisoning the approval record.
 */
public interface ApprovalActionExecutor {

    /** The action identifier this executor handles, e.g. {@code SECRET_DELETE}. */
    String action();

    /**
     * Performs the action.
     *
     * @return a human-readable description of what was done
     * @throws RuntimeException if the action could not be completed
     */
    String execute(ApprovalRequest request);
}
