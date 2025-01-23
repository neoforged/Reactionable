package net.neoforged.automation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.neoforged.automation.Configuration;
import net.neoforged.automation.command.api.GHCommandContext;
import net.neoforged.automation.util.FunctionalInterfaces;
import net.neoforged.automation.webhook.handler.AutomaticLabelHandler;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Commands {
    public static CommandDispatcher<GHCommandContext> register(CommandDispatcher<GHCommandContext> dispatcher) {
        dispatcher.register(literal("run")
                .requires(Requirement.IS_PR.and(Requirement.IS_MAINTAINER.or(Requirement.IS_PR)))
                .then(argument("tasks", StringArgumentType.greedyString())
                        .executes(FunctionalInterfaces.throwingCommand(context -> {
                            var pr = context.getSource().pullRequest();
                            var config = Configuration.get();
                            var repoConfig = Configuration.get(context.getSource().repository());
                            if (repoConfig.baseRunCommand() != null) {
                                var tasks = context.getArgument("tasks", String.class).trim();
                                var comment = pr.comment("Running Gradle tasks `" + tasks + "`...");
                                FormattingCommand.run(
                                        context.getSource().gitHub(), pr,
                                        config.prActions(), repoConfig,
                                        Arrays.stream(tasks.split(" ")).map(t -> "./gradlew " + t).toList(),
                                        err -> {
                                            context.getSource().onError().run();
                                            try {
                                                context.getSource().issue()
                                                        .comment("Workflow failed: " + err.getHtmlUrl());
                                            } catch (Exception ex) {
                                                throw new RuntimeException(ex);
                                            }
                                        }, () -> {
                                            context.getSource().onSuccess().run();
                                            FunctionalInterfaces.ignoreExceptions(comment::delete);
                                        }
                                );
                            }
                            return GHCommandContext.DEFERRED_RESPONSE;
                        }))));

        dispatcher.register(literal("backport")
                .requires(Requirement.IS_PR.and(Requirement.IS_MAINTAINER))
                .then(argument("branch", StringArgumentType.greedyString())
                        .executes(FunctionalInterfaces.throwingCommand(context -> {
                            var source = context.getSource();
                            var pr = context.getSource().pullRequest();

                            var branch = context.getArgument("branch", String.class).trim();
                            var comment = pr.comment("Backporting to `" + branch + "`...");

                            boolean existed;
                            try {
                                context.getSource().repository().getBranch("backport/" + branch + "/" + pr.getNumber());
                                existed = true;
                            } catch (IOException ex) {
                                existed = false;
                            }

                            boolean didExist = existed;

                            BackportCommand.generatePatch(
                                    context.getSource().gitHub(), pr,
                                    Configuration.get(), branch,
                                    (runner, err) -> {
                                        context.getSource().onError().run();
                                        try {
                                            var message = new StringBuilder();
                                            message.append("@").append(source.user().getLogin()).append(" backport to ")
                                                            .append(branch).append(" failed.\n\n");

                                            message.append("<details>\n\n<summary>Click for failure reason</summary>\n\n");

                                            message.append(err).append("\n").append(runner.getRun(context.getSource().gitHub()).getHtmlUrl()).append("\n\n</details>");

                                            source.issue().comment(message.toString());
                                        } catch (Exception ex) {
                                            throw new RuntimeException(ex);
                                        }
                                    }, (newBranch) -> {
                                        if (!didExist) {
                                            var body = new StringBuilder();

                                            body.append("Backport of #").append(pr.getNumber()).append(" to ").append(branch);

                                            var fixes = AutomaticLabelHandler.getClosingIssues(source.gitHub(), source.pullRequest())
                                                    .stream()
                                                    .map(n -> "Fixes #" + n.issueInfo.number + " on " + branch)
                                                    .collect(Collectors.joining("\n"));

                                            if (!fixes.isBlank()) {
                                                body.append("\n\n").append(fixes);
                                            }

                                            var createdPr = context.getSource().repository()
                                                    .createPullRequest(
                                                            "Backport to " + branch + ": " + pr.getTitle(),
                                                            newBranch, branch, body.toString()
                                                    );

                                            createdPr.addLabels(source.pullRequest().getLabels()
                                                    .stream()
                                                    .filter(l -> !l.getName().startsWith("1."))
                                                    .toList());

                                            context.getSource().pullRequest()
                                                    .comment("Created backport PR: #" + createdPr.getNumber());
                                        }

                                        context.getSource().onSuccess().run();
                                        FunctionalInterfaces.ignoreExceptions(comment::delete);
                                    }
                            );
                            return GHCommandContext.DEFERRED_RESPONSE;
                        }))));

        return dispatcher;
    }

    private static ExtendedLiteralArgumentBuilder<GHCommandContext> literal(String name) {
        return new ExtendedLiteralArgumentBuilder<>(name);
    }

    private static <T> RequiredArgumentBuilder<GHCommandContext, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static class ExtendedLiteralArgumentBuilder<T> extends LiteralArgumentBuilder<T> {

        protected ExtendedLiteralArgumentBuilder(String literal) {
            super(literal);
        }

        public LiteralArgumentBuilder<T> requires(FunctionalInterfaces.PredException<T> requirement) {
            return this.requires(FunctionalInterfaces.wrapPred(requirement));
        }
    }
}
