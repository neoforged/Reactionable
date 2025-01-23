package net.neoforged.automation.webhook.handler;

import com.github.api.GetPullRequestQuery;
import com.github.api.fragment.PullRequestInfo;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.util.Label;
import net.neoforged.automation.webhook.impl.ActionBasedHandler;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AutomaticLabelHandler implements ActionBasedHandler<GHEventPayload.PullRequest> {
    private static final Pattern BACKPORT_OF_PATTERN = Pattern.compile("Backport of #(\\d+) to ([\\w.]+)");

    @Override
    public void handle(GitHub gitHub, GHEventPayload.PullRequest payload, GHAction action) throws Exception {
        var pr = payload.getPullRequest();
        if (pr.getUser().getLogin().endsWith("[bot]") && pr.getBody().startsWith("Backport of #")) {
            // Copy over non-version labels of backports

            var matcher = BACKPORT_OF_PATTERN.matcher(pr.getBody());
            if (matcher.find()) {
                int latestVersion = Integer.parseInt(matcher.group(1));
                var backportVersion = matcher.group(2);

                var latestPr = payload.getRepository().getPullRequest(latestVersion);
                pr.addLabels(latestPr.getLabels()
                        .stream()
                        .filter(l -> !l.getName().startsWith("1."))
                        .toList());

                var fixes = getClosingIssues(gitHub, latestPr)
                        .stream()
                        .map(n -> "Fixes #" + n.issueInfo.number + " on " + backportVersion)
                        .collect(Collectors.joining("\n"));

                if (!fixes.isBlank()) {
                    pr.setBody(pr.getBody() + "\n\n" + fixes);
                }
            }

            Label.BACKPORT.label(pr); // And mark this PR as a backport
            return;
        }

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

    private List<PullRequestInfo.Node1> getClosingIssues(GitHub gitHub, GHPullRequest pr) throws Exception {
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

    private Set<String> getClosingIssueLabels(GitHub gitHub, GHPullRequest pr) throws Exception {
        var labels = new HashSet<String>();
        for (var issueNode : getClosingIssues(gitHub, pr)) {
            var issue = issueNode.issueInfo;
            issue.labels.nodes.forEach(n -> labels.add(n.name.toLowerCase(Locale.ROOT)));
        }
        return labels;
    }
}
