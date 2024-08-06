package net.neoforged.automation.webhook.handler;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.impl.ActionBasedHandler;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;
import org.kohsuke.github.LockReason;

import java.io.IOException;
import java.util.Locale;

public class LabelLockHandler implements ActionBasedHandler<GHEventPayload.Issue> {

    @Override
    public void handle(GitHub gitHub, GHEventPayload.Issue payload, GHAction action) throws IOException {
        switch (action) {
            case LABELED -> onLabelLock(payload);
            case UNLABELED -> onLabelLockRemove(payload);
        }
    }

    public static void onLabelLock(GHEventPayload.Issue payload) throws IOException {
        final var lock = Configuration.get(payload.getRepository())
                .labelLocks().get(payload.getLabel().getName());
        if (lock == null) return;

        if (lock.message() != null) {
            payload.getIssue().comment(lock.message());
        }

        if (lock.close()) {
            GitHubAccessor.edit(payload.getIssue())
                    .edit("state", "closed")
                    .edit("state_reason", "not_planned")
                    .send();
        }

        if (lock.lock()) {
            if (lock.lockReason() == null) {
                payload.getIssue().lock();
            } else {
                GitHubAccessor.lock(payload.getIssue(), LockReason.valueOf(lock.lockReason().toUpperCase(Locale.ROOT)));
            }
        }
    }

    public static void onLabelLockRemove(GHEventPayload.Issue payload) throws IOException {
        final var lock = Configuration.get(payload.getRepository())
                .labelLocks().get(payload.getLabel().getName());
        if (lock == null) return;

        if (lock.lock()) payload.getIssue().unlock();
        if (lock.close()) payload.getIssue().reopen();
    }
}
