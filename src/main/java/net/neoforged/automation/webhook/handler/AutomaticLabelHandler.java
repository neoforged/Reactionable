package net.neoforged.automation.webhook.handler;

import com.github.api.GetPullRequestQuery;
import com.github.api.fragment.PullRequestInfo;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.impl.ActionBasedHandler;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AutomaticLabelHandler implements ActionBasedHandler<GHEventPayload.PullRequest> {
    @Override
    public void handle(GitHub gitHub, GHEventPayload.PullRequest payload, GHAction action) throws Exception {
        var pr = payload.getPullRequest();
        var labels = getClosingIssueLabels(gitHub, pr);

        // bug and enhancement are exclusive
        if (labels.contains("bug")) {
            pr.addLabels("bug");
        } else if (labels.contains("enhancement")) {
            pr.addLabels("enhancement");
        }

        if (labels.contains("regression")) {
            pr.addLabels("regression");
        }
    }

    private Set<String> getClosingIssueLabels(GitHub gitHub, GHPullRequest pr) throws Exception {
        var labels = new HashSet<String>();
        for (var issueNode : getClosingIssues(gitHub, pr)) {
            var issue = issueNode.issueInfo;
            issue.labels.nodes.forEach(n -> labels.add(n.name.toLowerCase(Locale.ROOT)));
        }
        return labels;
    }

    public static List<PullRequestInfo.Node1> getClosingIssues(GitHub gitHub, GHPullRequest pr) throws IOException {
        return GitHubAccessor.graphQl(gitHub, GetPullRequestQuery.builder()
                .name(pr.getRepository().getName())
                .owner(pr.getRepository().getOwnerName())
                .number(pr.getNumber())
                .build())
                .repository
                .pullRequest
                .pullRequestInfo
                .closingIssuesReferences
                .nodes;
    }
}
