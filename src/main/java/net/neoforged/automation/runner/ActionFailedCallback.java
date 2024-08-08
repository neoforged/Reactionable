package net.neoforged.automation.runner;

import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;

@FunctionalInterface
public interface ActionFailedCallback {
    void onFailed(GitHub gitHub, GHWorkflowRun run) throws Exception;
}
