package net.neoforged.automation.webhook.handler;

import com.github.api.GetPullRequestQuery;
import net.neoforged.automation.webhook.impl.EventHandler;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.util.HashSet;
import java.util.regex.Pattern;

public record ReleaseMessageHandler(GitHub releasesApp) implements EventHandler<GHEventPayload.Status> {
    private static final Pattern CLOSE_REFERENCE = Pattern.compile("(?mi)(?<type>(?:close|fix|resolve)(?:s|d|es|ed)?) #(?<number>\\d+)");

    @Override
    public void handle(GitHub gitHub, GHEventPayload.Status payload) throws Exception {
        if (payload.getState() == GHCommitState.SUCCESS && payload.getContext().equals("Publishing") && payload.getDescription().startsWith("Version: ")) {
            var version = payload.getDescription().replace("Version: ", "");

            var closedIssues = new HashSet<Integer>();
            var baseIssueComment = "\uD83D\uDE80 This issue has been resolved in " + payload.getRepository().getName() + " version `" + version + "`.";

            for (var pr : payload.getCommit().listPullRequests()) {
                if (pr.getMergedAt() == null) return;

                var repo = releasesApp.getRepository(payload.getRepository().getFullName());

                baseIssueComment = "\uD83D\uDE80 This issue has been resolved in " + repo.getName() + " version `"
                        + version + "`, as part of #" + pr.getNumber() + ".";

                repo.getPullRequest(pr.getNumber())
                        .comment("\uD83D\uDE80 This PR has been released as " + repo.getName() + " version `" + version + "`.");

                for (var issueNode : GitHubAccessor.graphQl(gitHub, GetPullRequestQuery.builder()
                                .name(payload.getRepository().getName())
                                .owner(payload.getRepository().getOwnerName())
                                .number(pr.getNumber())
                                .build())
                        .repository
                        .pullRequest
                        .pullRequestInfo
                        .closingIssuesReferences
                        .nodes) {
                    repo.getIssue(issueNode.issueInfo.number)
                            .comment(baseIssueComment);
                    closedIssues.add(issueNode.issueInfo.number);
                }
            }

            var matcher = CLOSE_REFERENCE.matcher(payload.getCommit().getCommitShortInfo().getMessage());
            while (matcher.find()) {
                var number = Integer.parseInt(matcher.group("number"));
                if (closedIssues.add(number)) {
                    var repo = releasesApp.getRepository(payload.getRepository().getFullName());
                    repo.getIssue(number).comment(baseIssueComment);
                }
            }
        }
    }
}
