package net.neoforged.automation.webhook.label;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

public record BlockPRHandler() implements LabelHandler {
    private static final String NAME = "PR Block";

    @Override
    public void onLabelAdded(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {
        if (issue instanceof GHPullRequest pr) {
            block(pr);
        }
    }

    @Override
    public void onLabelRemoved(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {
        if (issue instanceof GHPullRequest pr) {
            issue.getRepository()
                    .createCheckRun(NAME, pr.getHead().getSha())
                    .withConclusion(GHCheckRun.Conclusion.SUCCESS)
                    .add(new GHCheckRunBuilder.Output("PR block label removed", "PR block label removed"))
                    .create();
        }
    }

    @Override
    public void onSynchronized(GitHub gitHub, GHUser actor, GHPullRequest pullRequest, GHLabel label) throws Exception {
        block(pullRequest);
    }

    private void block(GHPullRequest pr) throws Exception {
        pr.getRepository().createCheckRun(NAME, pr.getHead().getSha())
                .withConclusion(GHCheckRun.Conclusion.FAILURE)
                .add(new GHCheckRunBuilder.Output("PR blocked as requested through label", "PR blocked"))
                .create();
    }
}
