package net.neoforged.automation.webhook.handler;

import com.github.api.MinimizeCommentMutation;
import com.github.api.type.ReportedContentClassifiers;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.neoforged.automation.Configuration;
import net.neoforged.automation.command.api.GHCommandContext;
import net.neoforged.automation.util.FunctionalInterfaces;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.impl.ActionBasedHandler;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;
import org.kohsuke.github.ReactionContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record CommandHandler(CommandDispatcher<GHCommandContext> dispatcher) implements ActionBasedHandler<GHEventPayload.IssueComment> {
    public static final Logger LOGGER = LoggerFactory.getLogger(CommandHandler.class);

    @Override
    public void handle(GitHub gitHub, GHEventPayload.IssueComment payload, GHAction action) throws Exception {
        var config = Configuration.get().commands();
        var command = findCommand(payload.getComment().getBody());
        if (command == null) return;

        var context = new GHCommandContext(gitHub, payload, () -> {
            if (config.reactToComment()) {
                FunctionalInterfaces.ignoreExceptions(() -> payload.getComment().createReaction(ReactionContent.CONFUSED));
            }
        }, () -> {
            if (config.reactToComment()) {
                FunctionalInterfaces.ignoreExceptions(() -> payload.getComment().createReaction(ReactionContent.ROCKET));
            }
            if (command.commentOnlyCommand() && config.minimizeComment()) {
                FunctionalInterfaces.ignoreExceptions(() -> GitHubAccessor.graphQl(gitHub, MinimizeCommentMutation.builder()
                        .comment(payload.getComment().getNodeId())
                        .reason(ReportedContentClassifiers.RESOLVED)
                        .build()));
            }
        });
        var results = dispatcher.parse(command.command(), context);

        // If the command does not fully parse, then return
        if (results.getReader().getRemainingLength() > 0) {
            return;
        }

        try {
            final int result = dispatcher.execute(results);
            if (result == Command.SINGLE_SUCCESS) {
                context.onSuccess().run();
            }
        } catch (Exception e) {
            LOGGER.error("Error while executing command '{}': ", command, e);

            if (e instanceof CommandSyntaxException exception) {
                FunctionalInterfaces.ignoreExceptions(() -> payload.getIssue().comment("@%s, I encountered an exception executing that command: %s".formatted(
                        payload.getSender().getLogin(), exception.getMessage()
                )));
            }

            context.onError().run();
        }
    }

    public CommandData findCommand(String comment) {
        boolean commentOnlyCommand = false;
        String command = null;
        for (final String prefix : Configuration.get().commands().prefixes()) {
            if (comment.startsWith(prefix)) {
                // If at the start, consider the entire comment a command
                command = comment.substring(prefix.length());
                commentOnlyCommand = true;
            } else if (comment.contains(prefix)) {
                final var index = comment.indexOf(prefix);
                // If anywhere else, consider the line a command
                final var newLineIndex = comment.indexOf('\n', index);
                if (newLineIndex >= 0) {
                    command = comment.substring(index + prefix.length(), newLineIndex);
                } else {
                    command = comment.substring(index + prefix.length());
                }
            }

            if (command != null) {
                return new CommandData(commentOnlyCommand, command);
            }
        }

        return null;
    }

    public record CommandData(boolean commentOnlyCommand, String command) {}
}
