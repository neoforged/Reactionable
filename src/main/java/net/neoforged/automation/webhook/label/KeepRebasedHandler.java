package net.neoforged.automation.webhook.label;

import com.github.api.GetPullRequestQuery;
import com.github.api.type.MergeableState;
import net.neoforged.automation.webhook.handler.MergeConflictCheckHandler;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

public class KeepRebasedHandler implements LabelHandler {
    @Override
    public void onLabelAdded(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {
        while (MergeConflictCheckHandler.checkConflict(gitHub, GitHubAccessor.graphQl(gitHub, GetPullRequestQuery.builder()
                        .owner(issue.getRepository().getOwnerName())
                        .name(issue.getRepository().getName())
                        .number(issue.getNumber())
                        .build())
                .repository()
                .pullRequest().fragments().pullRequestInfo()) == MergeableState.UNKNOWN) {
            Thread.sleep(MergeConflictCheckHandler.PR_BASE_TIME * 1000);
        }
    }
}
