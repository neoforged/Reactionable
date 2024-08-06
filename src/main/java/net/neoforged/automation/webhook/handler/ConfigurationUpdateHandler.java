package net.neoforged.automation.webhook.handler;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.webhook.impl.EventHandler;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

import java.io.IOException;

public record ConfigurationUpdateHandler(Configuration.RepoLocation location) implements EventHandler<GHEventPayload.Push> {
    @Override
    public void handle(GitHub gitHub, GHEventPayload.Push payload) throws IOException {
        if (payload.getRepository().getFullName().equalsIgnoreCase(location.repo()) && payload.getRef().equals("refs/heads/" + location.branch())) {
            Configuration.load(gitHub, location);
        }
    }
}
