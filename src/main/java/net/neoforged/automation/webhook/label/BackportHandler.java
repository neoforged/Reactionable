package net.neoforged.automation.webhook.label;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.Main;
import net.neoforged.automation.command.BackportCommand;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record BackportHandler(String version) implements LabelHandler {
    private static final Set<Integer> RUNNING_PRS = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onLabelAdded(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {
        if (issue instanceof GHPullRequest pr) {
            RUNNING_PRS.add(pr.getNumber());
            run(gitHub, pr, actor);
        } else {
            issue.removeLabel(label.getName());
        }
    }

    @Override
    public void onSynchronized(GitHub gitHub, GHUser actor, GHPullRequest pullRequest, GHLabel label) throws Exception {
        Main.scheduleUntil(() -> {
            if (RUNNING_PRS.add(pullRequest.getNumber())) {
                run(gitHub, pullRequest, actor);
                return true;
            }

            return false;
        }, 30);
    }

    private void run(GitHub gitHub, GHPullRequest pr, GHUser source) throws Exception {
        BackportCommand.createOrUpdatePR(
                gitHub, pr,
                Configuration.get(), version,
                (runner, err) -> {
                    RUNNING_PRS.remove(pr.getNumber());

                    try {
                        var message = new StringBuilder();
                        message.append("@").append(source.getLogin()).append(" backport to ")
                                .append(version).append(" failed.\n\n");

                        message.append("<details>\n\n<summary>Click for failure reason</summary>\n\n");

                        message.append(err).append("\n").append(runner.getRun(gitHub).getHtmlUrl()).append("\n\n</details>");

                        pr.comment(message.toString());
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                },
                createdPr -> {
                    RUNNING_PRS.remove(pr.getNumber());

                    if (createdPr != null) {
                        pr.comment("Created backport PR: #" + createdPr.getNumber());
                    }
                }
        );
    }
}
