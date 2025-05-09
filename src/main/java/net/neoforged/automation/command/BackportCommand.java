package net.neoforged.automation.command;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.Main;
import net.neoforged.automation.runner.ActionExceptionHandler;
import net.neoforged.automation.runner.ActionRunner;
import net.neoforged.automation.runner.GitRunner;
import net.neoforged.automation.util.DiffUtils;
import net.neoforged.automation.util.FunctionalInterfaces;
import net.neoforged.automation.webhook.handler.AutomaticLabelHandler;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BackportCommand {
    public static void createOrUpdatePR(GitHub gh, GHPullRequest pr, Configuration configuration, String branch, ActionExceptionHandler exception,
                                        FunctionalInterfaces.ConsumerException<@Nullable GHPullRequest> onFinished,
                                        Set<String> ignoredLabels) throws IOException {
        boolean existed;
        try {
            pr.getRepository().getBranch("backport/" + branch + "/" + pr.getNumber());
            existed = true;
        } catch (IOException ex) {
            existed = false;
        }

        boolean didExist = existed;

        generateAndApply(
                gh, pr, configuration, branch, exception,
                (newBranch) -> {
                    if (!didExist) {
                        var body = new StringBuilder();

                        body.append("Backport of #").append(pr.getNumber()).append(" to ").append(branch);

                        var fixes = AutomaticLabelHandler.getClosingIssues(gh, pr)
                                .stream()
                                .map(n -> "Fixes #" + n.issueInfo.number + " on " + branch)
                                .collect(Collectors.joining("\n"));

                        if (!fixes.isBlank()) {
                            body.append("\n\n").append(fixes);
                        }

                        var createdPr = pr.getRepository()
                                .createPullRequest(
                                        "Backport to " + branch + ": " + pr.getTitle().replaceFirst("^[\\[\\(][\\d\\.]+[\\]\\)]|(Backport to [\\d.]+:)", "").trim(),
                                        newBranch, branch, body.toString()
                                );

                        var labelsToAdd = pr.getLabels()
                                .stream()
                                .filter(l -> {
                                    if (l.getName().startsWith("1.") || ignoredLabels.contains(l.getName())) {
                                        return false;
                                    }
                                    // We do not move over functional labels
                                    return configuration.getRepo(pr.getRepository()).getLabelHandler(l.getName()) == null;
                                })
                                .toList();

                        if (!labelsToAdd.isEmpty()) {
                            createdPr.addLabels(labelsToAdd);
                        }

                        onFinished.accept(createdPr);
                    } else {
                        onFinished.accept(null);
                    }
                }
        );
    }

    public static void generateAndApply(GitHub gh, GHPullRequest pr, Configuration configuration, String branch, ActionExceptionHandler exception, FunctionalInterfaces.ConsumerException<String> onSuccess) throws IOException {
        var backport = configuration.getRepo(pr.getRepository()).backport();
        Main.actionRunner(gh, configuration.prActions())
                .name("Backport " + pr.getRepository().getFullName() + " #" + pr.getNumber() + " to " + branch + ": generate patch")
                .run(runner -> {
                    runner.git("init");
                    runner.clone(pr.getRepository().getHtmlUrl() + ".git", "origin", pr.getBase().getSha());

                    runner.detectAndSetJavaVersion();

                    runner.runCaching(
                            "gradle-pr-" + pr.getRepository().getFullName() + "-" + pr.getNumber() + "-",
                            runner.resolveHome(".gradle/"),
                            System.currentTimeMillis() / 1000,
                            () -> {
                                var vars = computeVariables(pr, branch);

                                if (!backport.preApplyGenCommands().isEmpty()) {
                                    runner.log("Running pre-apply commands...");
                                    for (var cmd : backport.preApplyGenCommands()) {
                                        run(runner, cmd, vars);
                                    }
                                }

                                runner.writeFile("__diff", GitHubAccessor.readDiff(pr));
                                runner.git("apply", "--ignore-whitespace", "__diff");
                                runner.exec("rm", "__diff");

                                if (!backport.postApplyGenCommands().isEmpty()) {
                                    runner.log("Running post-apply commands...");
                                    for (var cmd : backport.postApplyGenCommands()) {
                                        run(runner, cmd, vars);
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

                    runner.detectAndSetJavaVersion();

                    runner.runCaching(
                            "gradle-branch-" + pr.getRepository().getFullName() + "-" + branch + "-",
                            runner.resolveHome(".gradle/"),
                            "backport-" + pr.getNumber() + "-" + System.currentTimeMillis() / 1000,
                            () -> {
                                var vars = computeVariables(pr, branch);

                                if (!backport.preApplyCommands().isEmpty()) {
                                    runner.log("Running pre-apply commands...");
                                    for (var cmd : backport.preApplyCommands()) {
                                        run(runner, cmd, vars);
                                    }
                                }

                                runner.saveCache("gradle", runner.resolveHome(".gradle/"));

                                runner.writeFile("__diff", diff);
                                try {
                                    runner.git("apply", "--ignore-whitespace", "--recount", "-C0", "--reject", "__diff");
                                } catch (ActionRunner.ExecutionException ignored) {
                                    // We ignore this as even with --reject the command exists with status code 1
                                }
                                runner.exec("rm", "__diff");

                                if (!backport.postApplyCommands().isEmpty()) {
                                    runner.log("Running post-apply commands...");
                                    for (var cmd : backport.postApplyCommands()) {
                                        run(runner, cmd, vars);
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

    private static void run(ActionRunner runner, Configuration.ConditionalValue<String> cmd, Map<String, ?> variables) {
        var c = cmd.get(runner, variables);
        if (c != null) {
            for (String subCommand : c.split("\n")) {
                if (!subCommand.isBlank()) {
                    runner.execFullCommand(subCommand.trim());
                }
            }
        }
    }

    private static Map<String, ?> computeVariables(GHPullRequest pr, String branch) {
        return Map.of(
                "pr", Map.of(
                        "base", pr.getBase().getRef(),
                        "changedFiles", DiffUtils.detectChangedFiles(GitHubAccessor.readDiff(pr).trim().split("\n"))
                ),
                "target", branch
        );
    }
}
