package net.neoforged.automation;

import com.mojang.brigadier.CommandDispatcher;
import io.javalin.Javalin;
import net.neoforged.automation.command.Commands;
import net.neoforged.automation.util.AuthUtil;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.handler.AutomaticLabelHandler;
import net.neoforged.automation.webhook.handler.CommandHandler;
import net.neoforged.automation.webhook.handler.ConfigurationUpdateHandler;
import net.neoforged.automation.webhook.handler.LabelEventHandler;
import net.neoforged.automation.webhook.handler.MergeConflictCheckHandler;
import net.neoforged.automation.webhook.handler.PRActionRunnerHandler;
import net.neoforged.automation.webhook.handler.ReleaseMessageHandler;
import net.neoforged.automation.webhook.handler.neo.VersionLabelHandler;
import net.neoforged.automation.webhook.impl.GitHubEvent;
import net.neoforged.automation.webhook.impl.WebhookHandler;
import org.kohsuke.github.GitHubAccessor;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class Main {
    private static final String NEOFORGE = "neoforged/NeoForge";
    public static final Logger LOGGER = LoggerFactory.getLogger("Reactionable");

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        var startupConfig = StartupConfiguration.load(Path.of("config.properties"));

        var gitHub = new GitHubBuilder()
                .withAuthorizationProvider(AuthUtil.githubApp(
                        startupConfig.get("gitHubAppId", ""),
                        AuthUtil.parsePKCS8(startupConfig.get("gitHubAppKey", "")),
                        ghApp -> ghApp.getInstallationByOrganization(startupConfig.get("gitHubAppOrganization", ""))
                ))
                .build();

        var location = Configuration.load(gitHub, startupConfig);

        var webhook = setupWebhookHandlers(startupConfig, new WebhookHandler(startupConfig, gitHub), location);

        var app = Javalin.create(cfg -> {
                    cfg.useVirtualThreads = true;
                })
                .post("/webhook", webhook)
                .start(startupConfig.getInt("port", 8080));

        LOGGER.warn("Started up! Logged as {} on GitHub", GitHubAccessor.getApp(gitHub).getSlug());
    }

    public static WebhookHandler setupWebhookHandlers(StartupConfiguration startupConfig, WebhookHandler handler, Configuration.RepoLocation location) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return handler
                .register(new MergeConflictCheckHandler())
                .register(new LabelEventHandler())
                .registerHandler(GitHubEvent.PUSH, new ConfigurationUpdateHandler(location))
                .registerHandler(GitHubEvent.STATUS, new ReleaseMessageHandler(new GitHubBuilder()
                        .withAuthorizationProvider(AuthUtil.githubApp(
                                startupConfig.get("releasesGitHubAppId", ""),
                                AuthUtil.parsePKCS8(startupConfig.get("releasesGitHubAppKey", "")),
                                ghApp -> ghApp.getInstallationByOrganization(startupConfig.get("releasesGitHubAppOrganization", ""))
                        ))
                        .build()))
                .registerFilteredHandler(GitHubEvent.WORKFLOW_RUN, new PRActionRunnerHandler(), GHAction.COMPLETED)
                .registerFilteredHandler(GitHubEvent.ISSUE_COMMENT, new CommandHandler(Commands.register(new CommandDispatcher<>())), GHAction.CREATED)
                .registerFilteredHandler(GitHubEvent.PULL_REQUEST, new AutomaticLabelHandler(), GHAction.OPENED, GHAction.REOPENED)

                .registerFilteredHandler(GitHubEvent.PULL_REQUEST, new VersionLabelHandler().forRepository(NEOFORGE), GHAction.OPENED, GHAction.REOPENED);
    }
}
