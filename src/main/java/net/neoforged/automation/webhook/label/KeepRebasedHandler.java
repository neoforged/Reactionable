package net.neoforged.automation.webhook.label;

import net.neoforged.automation.webhook.handler.MergeConflictCheckHandler;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

public class KeepRebasedHandler implements LabelHandler {
    @Override
    public void onLabelAdded(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {
        if (issue instanceof GHPullRequest pr) {
            MergeConflictCheckHandler.checkPR(gitHub, pr);
        }
    }
}
