package com.shandong.policyagent.rag;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class KnowledgeMigrationState {

    private final AtomicBoolean pending = new AtomicBoolean(false);
    private final AtomicBoolean failed = new AtomicBoolean(false);
    private volatile String summary = "idle";

    public void markPending(boolean enabled) {
        pending.set(enabled);
        failed.set(false);
        summary = enabled ? "pending" : "disabled";
    }

    public void markCompleted(String summary) {
        pending.set(false);
        failed.set(false);
        this.summary = summary;
    }

    public void markFailed(String summary) {
        pending.set(false);
        failed.set(true);
        this.summary = summary;
    }

    public boolean isPending() {
        return pending.get();
    }

    public boolean isFailed() {
        return failed.get();
    }

    public String getSummary() {
        return summary;
    }
}
