package net.neoforged.automation.runner;

import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;

import java.nio.file.Path;
import java.util.Map;

@FunctionalInterface
public interface ActionFinishedCallback {
    void onFinished(GitHub gitHub, GHWorkflowRun run, Map<String, Path> artifact) throws Exception;
}
