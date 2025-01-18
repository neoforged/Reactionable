package net.neoforged.automation.discord;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import io.javalin.Javalin;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.neoforged.automation.Main;
import net.neoforged.automation.StartupConfiguration;
import net.neoforged.automation.discord.command.GitHubCommand;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DiscordBot {
    public static final Logger LOGGER = LoggerFactory.getLogger(DiscordBot.class);

    private static volatile List<String> knownRepositories = List.of();

    public static void create(String token, GitHub gitHub, StartupConfiguration configuration, Javalin app) throws InterruptedException {
        var cfg = new OauthConfig(configuration.get("githubOauthClientId", ""), configuration.get("githubOauthClientSecret", ""),
                configuration.resolveUrl("serverUrl", "/oauth2/discord/github"));

        var jda = JDABuilder.createLight(token)
                .addEventListeners(createClient(gitHub, cfg))
                .build()
                .awaitReady();

        app.get("/oauth2/discord/github", new GitHubOauthLinkHandler(cfg));

        LOGGER.info("Discord bot started. Logged in as {}", jda.getSelfUser().getEffectiveName());

        Main.EXECUTOR.scheduleAtFixedRate(() -> updateRepos(gitHub), 0, 1, TimeUnit.HOURS);
    }

    private static CommandClient createClient(GitHub gitHub, OauthConfig config) {
        var builder = new CommandClientBuilder();
        builder.setOwnerId("0");
        builder.addSlashCommand(new GHLinkCommand(config.clientId, config.redirectUrl));
        builder.addSlashCommand(new GitHubCommand(gitHub));
        return builder.build();
    }

    private static void updateRepos(GitHub gitHub) {
        try {
            synchronized (DiscordBot.class) {
                knownRepositories = gitHub.getInstallation().listRepositories().toList()
                        .stream()
                        .filter(r -> !r.isArchived() && !r.isPrivate())
                        .map(GHRepository::getFullName)
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void suggestRepositories(CommandAutoCompleteInteractionEvent event) {
        Predicate<String> filter = filterContainsCurrent(event);
        event.replyChoices(knownRepositories.stream().filter(filter)
                        .limit(25)
                        .map(o -> new Command.Choice(o, o))
                        .toList())
                .queue();
    }

    public static Consumer<CommandAutoCompleteInteractionEvent> suggestBranches(GitHub gitHub) {
        return event -> {
            var repoName = event.getOption("repo", OptionMapping::getAsString);
            if (repoName == null || repoName.isBlank()) return;

            try {
                var repo = gitHub.getRepository(repoName);

                event.replyChoices(repo.getBranches().keySet()
                        .stream().filter(filterContainsCurrent(event))
                        .limit(25)
                        .map(o -> new Command.Choice(o, o))
                        .toList()).queue();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static Predicate<String> filterContainsCurrent(CommandAutoCompleteInteractionEvent event) {
        var current = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        return current.isBlank() ? e -> true : e -> e.toLowerCase(Locale.ROOT).contains(current);
    }

    record OauthConfig(String clientId, String clientSecret, String redirectUrl) {
    }
}
