package net.neoforged.automation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.neoforged.automation.Configuration;
import net.neoforged.automation.command.api.GHCommandContext;
import net.neoforged.automation.util.FunctionalInterfaces;

public class Commands {
    public static CommandDispatcher<GHCommandContext> register(CommandDispatcher<GHCommandContext> dispatcher) {
        dispatcher.register(literal("applyFormatting")
                .requires(Requirement.IS_PR.and(Requirement.IS_MAINTAINER.or(Requirement.IS_PR)))
                .executes(FunctionalInterfaces.throwingCommand(context -> {
                    var pr = context.getSource().pullRequest();
                    var config = Configuration.get();
                    var repoConfig = Configuration.get(context.getSource().repository());
                    if (!repoConfig.formattingTasks().isEmpty()) {
                        var comment = pr.comment("Applying formatting...");
                        FormattingCommand.run(
                                context.getSource().gitHub(), pr,
                                config.prActions(), repoConfig,
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
