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
    private record PRRun(int number, String backportBranch) {}
    private static final Set<PRRun> RUNNING_PRS = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onLabelAdded(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {
        if (issue instanceof GHPullRequest pr) {
            // We cannot backport to the base version
            if (version().equals(pr.getBase().getRef())) {
                pr.removeLabel(label.getName());
            } else {
                RUNNING_PRS.add(new PRRun(pr.getNumber(), version()));
                run(gitHub, pr, actor, label);
            }
        } else {
            issue.removeLabel(label.getName());
        }
    }

    @Override
    public void onSynchronized(GitHub gitHub, GHUser actor, GHPullRequest pullRequest, GHLabel label) throws Exception {
        Main.scheduleUntil(() -> {
            if (RUNNING_PRS.add(new PRRun(pullRequest.getNumber(), version()))) {
                run(gitHub, pullRequest, actor, label);
                return true;
            }

            return false;
        }, 30);
    }

    private void run(GitHub gitHub, GHPullRequest pr, GHUser source, GHLabel label) throws Exception {
        BackportCommand.createOrUpdatePR(
                gitHub, pr,
                Configuration.get(), version,
                (runner, err) -> {
                    RUNNING_PRS.remove(new PRRun(pr.getNumber(), version()));

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

                    // Remove the label after a failure so it can easily be triggered again
                    pr.removeLabel(label.getName());
                },
                createdPr -> {
                    RUNNING_PRS.remove(new PRRun(pr.getNumber(), version()));

                    if (createdPr != null) {
                        pr.comment("Created backport PR: #" + createdPr.getNumber());
                    }

                    // If the PR is merged then it's not going to have further updates so we can remove the label
                    if (pr.isMerged()) {
                        pr.removeLabel(label.getName());
                    }
                },
                Set.of(label.getName())
        );
    }
}
