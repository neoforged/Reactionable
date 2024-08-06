package net.neoforged.automation.webhook.impl;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

import java.io.IOException;

public interface EventHandler<T extends GHEventPayload> {
    void handle(GitHub gitHub, T payload) throws Exception;

    default EventHandler<T> and(EventHandler<T> other) {
        return (gitHub, payload) -> {
            this.handle(gitHub, payload);
            other.handle(gitHub, payload);
        };
    }
}
