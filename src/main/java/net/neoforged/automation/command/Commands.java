package net.neoforged.automation.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.neoforged.automation.Configuration;
import net.neoforged.automation.command.api.GHCommandContext;
import net.neoforged.automation.util.FunctionalInterfaces;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

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

                            BackportCommand.createOrUpdatePR(
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
                                    },
                                    createdPr -> {
                                        if (createdPr != null) {
                                            context.getSource().pullRequest().comment("Created backport PR: #" + createdPr.getNumber());
                                        }
                                        context.getSource().onSuccess().run();
                                        FunctionalInterfaces.ignoreExceptions(comment::delete);
                                    },
                                    Set.of()
                            );

                            return GHCommandContext.DEFERRED_RESPONSE;
                        }))));

        dispatcher.register(literal("benchmark")
                .requires(Requirement.IS_PR.and(Requirement.IS_MAINTAINER))
                .executes(FunctionalInterfaces.throwingCommand(context ->
                        benchmark(context, context.getSource().pullRequest().getBase().getRef(), "jmh")))
                .then(argument("against", StringArgumentType.word())
                        .executes(FunctionalInterfaces.throwingCommand(context ->
                                benchmark(context, context.getArgument("against", String.class), "jmh")))
                        .then(argument("command", StringArgumentType.greedyString())
                                .executes(FunctionalInterfaces.throwingCommand(context ->
                                        benchmark(context, context.getArgument("against", String.class), context.getArgument("command", String.class)))))));

        return dispatcher;
    }

    private static int benchmark(CommandContext<GHCommandContext> context, String against, String command) throws IOException {
        var source = context.getSource();
        BenchmarkCommand.benchmark(
                source.gitHub(), source.pullRequest(), Configuration.get(),
                against, command, new CommandProgressListener(source, "Running benchmark..."),
                compareUrl -> {
                    source.issue().comment("@" + source.user().getLogin() + " benchmark complete. URL to comparison is available [here](" + compareUrl + ").");
                    source.onSuccess().run();
                }
        );
        return GHCommandContext.DEFERRED_RESPONSE;
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
