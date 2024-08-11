package net.neoforged.automation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.neoforged.automation.Configuration;
import net.neoforged.automation.command.api.GHCommandContext;
import net.neoforged.automation.util.FunctionalInterfaces;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Commands {
    public static CommandDispatcher<GHCommandContext> register(CommandDispatcher<GHCommandContext> dispatcher) {
        dispatcher.register(literal("run")
                .requires(Requirement.IS_PR.and(Requirement.IS_MAINTAINER.or(Requirement.IS_PR)))
                    .then(argument("tasks", StringArgumentType.greedyString()))
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
                                    Arrays.stream(tasks.split(" ")).map(t -> "./gradlew " + t).collect(Collectors.joining(" && ")),
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
                    })));

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
