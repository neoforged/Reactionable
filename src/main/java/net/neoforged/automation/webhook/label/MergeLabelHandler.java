package net.neoforged.automation.webhook.label;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

public record MergeLabelHandler(@JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES) GHPullRequest.MergeMethod method) implements LabelHandler {
    @Override
    public void onLabelAdded(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {
        if (!(issue instanceof GHPullRequest pr)) return;

        final String title = pr.getTitle() + " (#" + pr.getNumber() + ")";
        GitHubAccessor.merge(pr, title, null, method);
    }
}
