package net.neoforged.automation.runner;

import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;

import java.nio.file.Path;

@FunctionalInterface
public interface ActionFinishedCallback {
    void onFinished(GitHub gitHub, GHWorkflowRun run, Path artifact) throws Exception;
}
