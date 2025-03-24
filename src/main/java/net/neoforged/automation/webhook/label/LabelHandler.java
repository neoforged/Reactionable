package net.neoforged.automation.webhook.label;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import java.util.Map;

public interface LabelHandler {
    Map<String, Class<? extends LabelHandler>> TYPES = Map.of(
            "lock", LockLabelHandler.class,
            "merge", MergeLabelHandler.class,
            "keep-rebased", KeepRebasedHandler.class,
            "block", BlockPRHandler.class,
            "backport", BackportHandler.class
    );

    default void onLabelAdded(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {

    }

    default void onLabelRemoved(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {

    }

    default void onSynchronized(GitHub gitHub, GHUser actor, GHPullRequest pullRequest, GHLabel label) throws Exception {

    }

}
