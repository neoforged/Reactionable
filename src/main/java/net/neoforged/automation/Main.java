package net.neoforged.automation;

import io.javalin.Javalin;
import net.neoforged.automation.util.AuthUtil;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.handler.ConfigurationUpdateHandler;
import net.neoforged.automation.webhook.handler.LabelLockHandler;
import net.neoforged.automation.webhook.handler.MergeConflictCheckHandler;
import net.neoforged.automation.webhook.impl.GitHubEvent;
import net.neoforged.automation.webhook.impl.WebhookHandler;
import org.kohsuke.github.GitHubBuilder;

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
                .build();

        var location = Configuration.load(gitHub, startupConfig);

        var webhook = setupWebhookHandlers(new WebhookHandler(startupConfig, gitHub), location);

        var app = Javalin.create(cfg -> {
                    cfg.useVirtualThreads = true;
                })
                .post("/webhook", webhook)
                .start(startupConfig.getInt("port", 8080));
    }

    public static WebhookHandler setupWebhookHandlers(WebhookHandler handler, Configuration.RepoLocation location) {
        return handler
                .register(new MergeConflictCheckHandler())
                .registerHandler(GitHubEvent.PUSH, new ConfigurationUpdateHandler(location))
                .registerFilteredHandler(GitHubEvent.ISSUES, new LabelLockHandler(), GHAction.LABELED, GHAction.UNLABELED);
    }
}
