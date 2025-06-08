package net.neoforged.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mojang.brigadier.CommandDispatcher;
import io.javalin.Javalin;
import net.neoforged.automation.command.Commands;
import net.neoforged.automation.db.Database;
import net.neoforged.automation.discord.DiscordBot;
import net.neoforged.automation.runner.ActionRunnerHandler;
import net.neoforged.automation.util.AuthUtil;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.handler.AutomaticLabelHandler;
import net.neoforged.automation.webhook.handler.CommandHandler;
import net.neoforged.automation.webhook.handler.ConfigurationUpdateHandler;
import net.neoforged.automation.webhook.handler.LabelEventHandler;
import net.neoforged.automation.webhook.handler.MergeConflictCheckHandler;
import net.neoforged.automation.webhook.handler.ReleaseMessageHandler;
import net.neoforged.automation.webhook.handler.neo.VersionLabelHandler;
import net.neoforged.automation.webhook.impl.GitHubEvent;
import net.neoforged.automation.webhook.impl.WebhookHandler;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    private static final String NEOFORGE = "neoforged/NeoForge";
    public static final Logger LOGGER = LoggerFactory.getLogger("Reactionable");
    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("scheduled-", 0).factory());
    public static final ObjectMapper JSON = new ObjectMapper();

    public static FileHostService fileHostService;

    private static ActionRunnerHandler actionRunner;

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InterruptedException {
        var startupConfig = StartupConfiguration.load(Path.of("config.properties"));

        Database.init();

        var gitHub = new GitHubBuilder()
                .withAuthorizationProvider(AuthUtil.githubApp(
                        startupConfig.get("gitHubAppId", ""),
                        AuthUtil.parsePKCS8(startupConfig.get("gitHubAppKey", "")),
                        ghApp -> ghApp.getInstallationByOrganization(startupConfig.get("gitHubAppOrganization", ""))
                ))
                .build();

        var location = Configuration.load(gitHub, startupConfig);

        var webhook = setupWebhookHandlers(startupConfig, new WebhookHandler(startupConfig.get("webhookSecret", ""), gitHub), location);

        fileHostService = new FileHostService(Path.of("files"), startupConfig);

        actionRunner = new ActionRunnerHandler(startupConfig.resolveUrl("serverUrl", "/runner/<id>/ws"), Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name("action-runner-", 0)
                .uncaughtExceptionHandler((t, e) -> LOGGER.error("Caught exception running action runner handler on thread {}:", t, e))
                .factory()));

        var app = Javalin.create(cfg -> {
                    cfg.useVirtualThreads = true;
                })
                .post("/webhook", webhook)
                .ws("/runner/<id>/ws", actionRunner)

                .get("/file/<file>", fileHostService::get)

                .start(startupConfig.getInt("port", 8080));

        LOGGER.warn("Started up! Logged as {} on GitHub", GitHubAccessor.getApp(gitHub).getSlug());

        var discordToken = startupConfig.get("discordToken", "");
        if (!discordToken.isBlank()) {
            DiscordBot.create(discordToken, gitHub, startupConfig, app);
        }
    }

    public static ActionRunnerHandler.Builder actionRunner(GitHub gitHub, Configuration.PRActions config) {
        return actionRunner.builder(gitHub, config);
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
                .registerFilteredHandler(GitHubEvent.ISSUE_COMMENT, new CommandHandler(Commands.register(new CommandDispatcher<>())), GHAction.CREATED)
                .registerFilteredHandler(GitHubEvent.PULL_REQUEST, new AutomaticLabelHandler(), GHAction.OPENED, GHAction.REOPENED)

                .registerFilteredHandler(GitHubEvent.PULL_REQUEST, new VersionLabelHandler().forRepository(NEOFORGE), GHAction.OPENED, GHAction.REOPENED);
    }

    /**
     * Keep scheduling the runnable until it returns {@code true}.
     */
    public static void scheduleUntil(Callable<Boolean> runnable, int rateSeconds) {
        AtomicReference<Callable<Void>> run = new AtomicReference<>();
        run.set(() -> {
            if (!runnable.call()) {
                EXECUTOR.schedule(run.get(), rateSeconds, TimeUnit.SECONDS);
            }
            return null;
        });
        EXECUTOR.schedule(run.get(), 0, TimeUnit.SECONDS);
    }
}
