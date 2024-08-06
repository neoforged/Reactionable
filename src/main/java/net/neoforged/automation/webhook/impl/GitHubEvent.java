package net.neoforged.automation.webhook.impl;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class GitHubEvent<T extends GHEventPayload> {
    public static final Map<String, GitHubEvent<?>> BY_NAME = new HashMap<>();

    public static final GitHubEvent<GHEventPayload.Issue> ISSUES = create("issues", GHEventPayload.Issue.class);
    public static final GitHubEvent<GHEventPayload.PullRequest> PULL_REQUEST = create("pull_request", GHEventPayload.PullRequest.class);
    public static final GitHubEvent<GHEventPayload.IssueComment> ISSUE_COMMENT = create("issue_comment", GHEventPayload.IssueComment.class);
    public static final GitHubEvent<GHEventPayload.Push> PUSH = create("push", GHEventPayload.Push.class);

    private final Class<T> type;
    private GitHubEvent(Class<T> type) {
        this.type = type;
    }

    public T parse(GitHub gitHub, byte[] content) throws IOException {
        return GitHubAccessor.parseEventPayload(gitHub, content, type);
    }

    private static <T extends GHEventPayload> GitHubEvent<T> create(String name, Class<T> type) {
        var event = new GitHubEvent<>(type);
        BY_NAME.put(name, event);
        return event;
    }
}
