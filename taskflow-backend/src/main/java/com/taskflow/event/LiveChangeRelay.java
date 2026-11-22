package com.taskflow.event;

import com.taskflow.service.LiveEventCoalescer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges internal committed domain changes to the live-update pipeline.
 *
 * <p>Listening in {@link TransactionPhase#AFTER_COMMIT} guarantees that
 * rolled-back operations never produce a live update. {@code fallbackExecution}
 * keeps non-transactional callers (seeders, direct service calls) working
 * without weakening the after-commit rule for transactional callers.</p>
 */
@Component
public class LiveChangeRelay {

    private final LiveEventCoalescer coalescer;

    public LiveChangeRelay(LiveEventCoalescer coalescer) {
        this.coalescer = coalescer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommittedChange(LiveChangeEvent event) {
        coalescer.enqueue(event);
    }
}
