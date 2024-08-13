package net.neoforged.automation;

import com.mojang.brigadier.CommandDispatcher;
import io.javalin.Javalin;
import net.neoforged.automation.command.Commands;
import net.neoforged.automation.util.AuthUtil;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.handler.CommandHandler;
import net.neoforged.automation.webhook.handler.ConfigurationUpdateHandler;
import net.neoforged.automation.webhook.handler.LabelLockHandler;
import net.neoforged.automation.webhook.handler.MergeConflictCheckHandler;
import net.neoforged.automation.webhook.handler.PRActionRunnerHandler;
import net.neoforged.automation.webhook.handler.ReleaseMessageHandler;
import net.neoforged.automation.webhook.impl.GitHubEvent;
import net.neoforged.automation.webhook.impl.WebhookHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.HttpClientGitHubConnector;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class Main {
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        var startupConfig = StartupConfiguration.load(Path.of("config.properties"));

        var gitHub = new GitHubBuilder()
                .withAuthorizationProvider(AuthUtil.githubApp(
                        startupConfig.get("gitHubAppId", ""),
                        AuthUtil.parsePKCS8(startupConfig.get("gitHubAppKey", "")),
                        ghApp -> ghApp.getInstallationByOrganization(startupConfig.get("gitHubAppOrganization", ""))
                ))
                .withConnector(new HttpClientGitHubConnector())
                .build();

        var location = Configuration.load(gitHub, startupConfig);

        var webhook = setupWebhookHandlers(startupConfig, new WebhookHandler(startupConfig, gitHub), location);

        var app = Javalin.create(cfg -> {
                    cfg.useVirtualThreads = true;
                })
                .post("/webhook", webhook)
                .start(startupConfig.getInt("port", 8080));
    }

    public static WebhookHandler setupWebhookHandlers(StartupConfiguration startupConfig, WebhookHandler handler, Configuration.RepoLocation location) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return handler
                .register(new MergeConflictCheckHandler())
                .registerHandler(GitHubEvent.PUSH, new ConfigurationUpdateHandler(location))
                .registerHandler(GitHubEvent.STATUS, new ReleaseMessageHandler(new GitHubBuilder()
                        .withAuthorizationProvider(AuthUtil.githubApp(
                                startupConfig.get("releasesGitHubAppId", ""),
                                AuthUtil.parsePKCS8(startupConfig.get("releasesGitHubAppKey", "")),
                                ghApp -> ghApp.getInstallationByOrganization(startupConfig.get("releasesGitHubAppOrganization", ""))
                        ))
                        .withConnector(new HttpClientGitHubConnector())
                        .build()))
                .registerFilteredHandler(GitHubEvent.ISSUES, new LabelLockHandler(), GHAction.LABELED, GHAction.UNLABELED)
                .registerFilteredHandler(GitHubEvent.WORKFLOW_RUN, new PRActionRunnerHandler(), GHAction.COMPLETED)
                .registerFilteredHandler(GitHubEvent.ISSUE_COMMENT, new CommandHandler(
                        Commands.register(new CommandDispatcher<>())
                ), GHAction.CREATED);
    }
}
