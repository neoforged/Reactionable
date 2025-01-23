package net.neoforged.automation.command;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.Main;
import net.neoforged.automation.runner.ActionExceptionHandler;
import net.neoforged.automation.runner.GitRunner;
import net.neoforged.automation.util.FunctionalInterfaces;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BackportCommand {
    public static void generatePatch(GitHub gh, GHPullRequest pr, Configuration configuration, String branch, ActionExceptionHandler exception, FunctionalInterfaces.ConsumerException<String> onSuccess) throws IOException {
        var backport = configuration.getRepo(pr.getRepository()).backport();
        Main.actionRunner(gh, configuration.prActions())
                .name("Backport " + pr.getRepository().getFullName() + " #" + pr.getNumber() + " to " + branch + ": generate patch")
                .run(runner -> {
                    runner.git("init");
                    runner.clone(pr.getRepository().getHtmlUrl() + ".git", "origin", pr.getBase().getRef());

                    runner.runCaching(
                            "gradle-pr-" + pr.getRepository().getFullName() + "-" + pr.getNumber() + "-",
                            runner.resolveHome(".gradle/"),
                            System.currentTimeMillis() / 1000,
                            () -> {
                                if (!backport.preApplyGenCommands().isEmpty()) {
                                    runner.log("Running pre-apply commands...");
                                    for (String cmd : backport.preApplyGenCommands()) {
                                        runner.execFullCommand(cmd);
                                    }
                                }

                                try (var is = pr.getDiffUrl().openStream()) {
                                    runner.writeFile("__diff", new String(is.readAllBytes()));
                                }
                                runner.git("apply", "--ignore-whitespace", "__diff");
                                runner.exec("rm", "__diff");

                                if (!backport.postApplyGenCommands().isEmpty()) {
                                    runner.log("Running post-apply commands...");
                                    for (String cmd : backport.postApplyGenCommands()) {
                                        runner.execFullCommand(cmd);
                                    }
                                }
                            }
                    );

                    var diff = runner.diff(backport.diffPattern());

                    applyPatch(gh, pr, configuration, diff, branch, exception, onSuccess);
                })
                .onFailure(exception)
                .queue();
    }

    public static void applyPatch(GitHub gh, GHPullRequest pr, Configuration configuration, String diff, String branch, ActionExceptionHandler exception, FunctionalInterfaces.ConsumerException<String> onSuccess) throws IOException {
        var backport = configuration.getRepo(pr.getRepository()).backport();
        Main.actionRunner(gh, configuration.prActions())
                .name("Backport " + pr.getRepository().getFullName() + " #" + pr.getNumber() + " to " + branch + ": apply patch")
                .run(runner -> {
                    runner.git("init");
                    runner.clone(pr.getRepository().getHtmlUrl() + ".git", "origin", branch);

                    runner.runCaching(
                            "gradle-branch-" + pr.getRepository().getFullName() + "-" + pr.getNumber() + "-",
                            runner.resolveHome(".gradle/"),
                            "backport-" + pr.getNumber() + "-" + System.currentTimeMillis() / 1000,
                            () -> {
                                if (!backport.preApplyCommands().isEmpty()) {
                                    runner.log("Running pre-apply commands...");
                                    for (String cmd : backport.preApplyCommands()) {
                                        runner.execFullCommand(cmd);
                                    }
                                }

                                runner.saveCache("gradle", runner.resolveHome(".gradle/"));

                                runner.writeFile("__diff", diff);
                                runner.git("apply", "--ignore-whitespace", "--theirs", "--3way", "--recount", "-C0", "__diff");
                                runner.exec("rm", "__diff");

                                if (!backport.postApplyCommands().isEmpty()) {
                                    runner.log("Running post-apply commands...");
                                    for (String cmd : backport.postApplyCommands()) {
                                        runner.execFullCommand(cmd);
                                    }
                                }
                            }
                    );

                    var newDiff = runner.diff().getBytes(StandardCharsets.UTF_8);

                    runner.log("Diff created... PR is being created... Stopping runner");
                    runner.stop();

                    GitRunner.setupRepo(gh, pr.getRepository(), branch, (dir, git, creds) -> {
                        git.apply().setPatch(new ByteArrayInputStream(newDiff)).call();

                        git.add().addFilepattern(".").call();

                        git.commit().setCredentialsProvider(creds)
                                .setCommitter(GitHubAccessor.ident(pr.getUser()))
                                .setMessage("Backport #" + pr.getNumber() + " to " + branch)
                                .setSign(false)
                                .setNoVerify(true)
                                .call();

                        var newBranch = "backport/" + branch + "/" + pr.getNumber();

                        git.push().setRemote("origin")
                                .setForce(true)
                                .setRefSpecs(new RefSpec("HEAD:refs/heads/" + newBranch))
                                .setCredentialsProvider(creds)
                                .call();

                        onSuccess.accept(newBranch);
                    });
                })
                .onFailure(exception)
                .queue();
    }
}
