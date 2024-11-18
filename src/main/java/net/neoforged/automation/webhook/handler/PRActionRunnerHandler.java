package net.neoforged.automation.webhook.handler;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.runner.PRActionRunner;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.impl.ActionBasedHandler;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;

public record PRActionRunnerHandler() implements ActionBasedHandler<GHEventPayload.WorkflowRun> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PRActionRunnerHandler.class);

    @Override
    public void handle(GitHub gitHub, GHEventPayload.WorkflowRun payload, GHAction action) throws Exception {
        var config = Configuration.get().prActions();
        if (payload.getRepository().getFullName().equalsIgnoreCase(config.repository()) && payload.getWorkflow().getPath().equals(".github/workflows/" + config.workflow().split("@")[0])) {
            var run = payload.getWorkflowRun();
            var id = UUID.fromString(run.getDisplayTitle());
            var runner = PRActionRunner.QUEUED.remove(id);
            if (runner == null) {
                LOGGER.error("Unknown action run with ID {}", id);
                return;
            }

            if (run.getConclusion() == GHWorkflowRun.Conclusion.SUCCESS) {
                var arts = run.listArtifacts().toList();
                var paths = new HashMap<String, Path>(arts.size());

                for (GHArtifact artifact : arts) {
                    var path = Files.createTempFile(artifact.getName(), ".zip");
                    artifact.download(input -> {
                        try (var out = Files.newOutputStream(path)) {
                            input.transferTo(out);
                        }
                        return path;
                    });
                    paths.put(artifact.getName(), path);
                }

                if (paths.isEmpty()) {
                    LOGGER.error("Action run {} didn't upload an artifact!", run.getHtmlUrl());
                    runner.failedCallback.onFailed(gitHub, run);
                    return;
                }


                runner.finishedCallback.onFinished(gitHub, run, paths);
                for (Path value : paths.values()) {
                    Files.delete(value);
                }
            } else {
                runner.failedCallback.onFailed(gitHub, run);
            }
        }
    }
}
