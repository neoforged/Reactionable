package net.neoforged.automation.webhook.impl;

import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

public interface EventHandler<T extends GHEventPayload> {
    void handle(GitHub gitHub, T payload) throws Exception;

    default EventHandler<T> forRepository(String name) {
        return (gitHub, payload) -> {
            if (payload.getRepository().getFullName().equalsIgnoreCase(name)) {
                this.handle(gitHub, payload);
            }
        };
    }
}
