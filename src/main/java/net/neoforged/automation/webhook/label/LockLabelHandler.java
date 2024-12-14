package net.neoforged.automation.webhook.label;

import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;
import org.kohsuke.github.LockReason;

import java.util.Locale;

public record LockLabelHandler(
        boolean lock,
        @Nullable String lockReason,
        boolean close,
        @Nullable String message
) implements LabelHandler {
    @Override
    public void onLabelAdded(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {
        if (message != null) {
            issue.comment(message);
        }

        if (close) {
            GitHubAccessor.edit(issue)
                    .edit("state", "closed")
                    .edit("state_reason", "not_planned")
                    .send();
        }

        if (lock) {
            if (lockReason == null) {
                issue.lock();
            } else {
                GitHubAccessor.lock(issue, LockReason.valueOf(lockReason.toUpperCase(Locale.ROOT)));
            }
        }
    }

    @Override
    public void onLabelRemoved(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {
        if (lock) issue.unlock();
        if (close) issue.reopen();
    }
}
