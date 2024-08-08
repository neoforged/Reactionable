package net.neoforged.automation.command.api;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public record GHCommandContext(GitHub gitHub, GHEventPayload.IssueComment payload, Runnable onError, Runnable onSuccess) {
    public static final int DEFERRED_RESPONSE = 2;

    public GHUser user() {
        return payload.getSender();
    }

    public GHRepository repository() {
        return issue().getRepository();
    }

    public GHIssue issue() {
        return payload.getIssue();
    }

    public GHPullRequest pullRequest() throws IOException {
        return repository().getPullRequest(issue().getNumber());
    }
}
