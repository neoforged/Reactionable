package net.neoforged.automation.runner;

import net.neoforged.automation.Configuration;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PRActionRunner {
    public static final Map<UUID, PRActionRunner> QUEUED = new ConcurrentHashMap<>();

    public final ActionFinishedCallback finishedCallback;
    public final ActionFailedCallback failedCallback;
    private final String uploadPattern;
    private final GHPullRequest pr;
    private final String command1, command2;

    private PRActionRunner(ActionFinishedCallback finishedCallback, ActionFailedCallback failedCallback, String uploadPattern, GHPullRequest pr, String command1, String command2) {
        this.finishedCallback = finishedCallback;
        this.failedCallback = failedCallback;
        this.uploadPattern = uploadPattern;
        this.pr = pr;
        this.command1 = command1;
        this.command2 = command2;
    }

    public void queue(GitHub gitHub, Configuration.PRActions config) throws IOException {
        var id = UUID.randomUUID();
        var repo = gitHub.getRepository(config.repository());
        var spl = config.workflow().split("@");
        repo.getWorkflow(spl[0])
                .dispatch(spl[1], Map.of(
                        "repository", pr.getRepository().getFullName(),
                        "pr", String.valueOf(pr.getNumber()),
                        "command1", command1,
                        "command2", command2,
                        "upload", uploadPattern,
                        "id", id.toString()
                ));
        QUEUED.put(id, this);
    }

    public static PRActionRunner.Builder builder(GHPullRequest pr) {
        return new Builder(pr);
    }

    public static class Builder {
        private final GHPullRequest pr;
        private String uploadPattern;
        private String cmd1, cmd2;
        private ActionFinishedCallback finished;
        private ActionFailedCallback failed;

        private Builder(GHPullRequest pr) {
            this.pr = pr;
        }

        public Builder command(List<String> command) {
            return command(command.getFirst(), command.size() > 1 ? command.get(1) : "");
        }

        public Builder command(String command1, String command2) {
            cmd1 = command1;
            cmd2 = command2;
            return this;
        }

        public Builder onFinished(ActionFinishedCallback callback) {
            this.finished = callback;
            return this;
        }

        public Builder onFailed(ActionFailedCallback failed) {
            this.failed = failed;
            return this;
        }

        public Builder upload(String pattern) {
            this.uploadPattern = pattern;
            return this;
        }

        public PRActionRunner build() {
            return new PRActionRunner(finished, failed, uploadPattern, pr, cmd1, cmd2);
        }
    }
}
