package net.neoforged.automation.webhook.impl;

import net.neoforged.automation.util.GHAction;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

public interface ActionBasedHandler<T extends GHEventPayload> {
    void handle(GitHub gitHub, T payload, GHAction action) throws Exception;

    default ActionBasedHandler<T> forRepository(String name) {
        return (gitHub, payload, act) -> {
            if (payload.getRepository().getFullName().equalsIgnoreCase(name)) {
                this.handle(gitHub, payload, act);
            }
        };
    }
}
