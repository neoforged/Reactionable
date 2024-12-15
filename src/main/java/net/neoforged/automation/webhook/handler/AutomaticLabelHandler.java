package net.neoforged.automation.webhook.handler;

import com.github.api.GetPullRequestQuery;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.impl.ActionBasedHandler;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AutomaticLabelHandler implements ActionBasedHandler<GHEventPayload.PullRequest> {
    @Override
    public void handle(GitHub gitHub, GHEventPayload.PullRequest payload, GHAction action) throws Exception {
        var labels = getClosingIssueLabels(gitHub, payload.getPullRequest());

        // bug and enhancement are exclusive
        if (labels.contains("bug")) {
            payload.getPullRequest().addLabels("bug");
        } else if (labels.contains("enhancement")) {
            payload.getPullRequest().addLabels("enhancement");
        }

        if (labels.contains("regression")) {
            payload.getPullRequest().addLabels("regression");
        }
    }

    private Set<String> getClosingIssueLabels(GitHub gitHub, GHPullRequest pr) throws Exception {
        var labels = new HashSet<String>();
        for (var issueNode : GitHubAccessor.graphQl(gitHub, GetPullRequestQuery.builder()
                        .name(pr.getRepository().getName())
                        .owner(pr.getRepository().getOwnerName())
                        .number(pr.getNumber())
                        .build())
                .repository
                .pullRequest
                .pullRequestInfo
                .closingIssuesReferences
                .nodes) {
            var issue = issueNode.issueInfo;
            issue.labels.nodes.forEach(n -> labels.add(n.name.toLowerCase(Locale.ROOT)));
        }
        return labels;
    }
}
