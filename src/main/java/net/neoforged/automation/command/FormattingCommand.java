package net.neoforged.automation.command;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.Main;
import net.neoforged.automation.runner.GitRunner;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public class FormattingCommand {

    public static void run(GitHub gh, GHPullRequest pr, Configuration.PRActions actions, Configuration.RepoConfiguration repoConfiguration, List<String> commands, Consumer<GHWorkflowRun> onFailure, Runnable onSuccess) throws IOException {
        Main.actionRunner(gh, actions)
                .run(runner -> {
                    runner.git("init");
                    runner.clone(pr.getRepository().getHtmlUrl() + ".git", "origin", "pull/" + pr.getNumber() + "/head");

                    runner.detectAndSetJavaVersion();

                    if (repoConfiguration.baseRunCommand() != null) {
                        runner.log("Running setup commands...");
                        for (String cmd : repoConfiguration.baseRunCommand().split("\n")) {
                            if (!cmd.isBlank()) {
                                runner.execFullCommand(cmd);
                            }
                        }
                    }

                    for (String command : commands) {
                        runner.execFullCommand(command);
                    }

                    var diff = runner.diff().getBytes(StandardCharsets.UTF_8);

                    runner.log("Finished executing commands... shutting down runner.");
                    runner.stop();

                    GitRunner.setupPR(gh, pr, (dir, git, creds) -> {
                        git.apply().setPatch(new ByteArrayInputStream(diff)).call();

                        git.add().addFilepattern(".").call();

                        git.commit().setCredentialsProvider(creds)
                                .setCommitter(creds.getPerson())
                                .setMessage("Run `" + String.join(" ", commands) + "`")
                                .setSign(false)
                                .setNoVerify(true)
                                .call();
                        git.push().setRemote("origin").setRefSpecs(new RefSpec("HEAD:refs/heads/" + pr.getHead().getRef())).setCredentialsProvider(creds).call();
                        onSuccess.run();
                    });
                })
                .onFailure((actionRunner, msg) -> onFailure.accept(actionRunner.getRun(gh)))
                .queue();
    }
}
