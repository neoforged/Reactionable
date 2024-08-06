package net.neoforged.automation.webhook.impl;

import net.neoforged.automation.util.GHAction;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.Locale;

public record ActionFilteredHandler<T extends GHEventPayload>(GHAction[] actions, ActionBasedHandler<T> handler) implements EventHandler<T> {
    @Override
    public void handle(GitHub gitHub, T payload) throws Exception {
        for (GHAction action : actions) {
            if (action.name().toLowerCase(Locale.ROOT).equals(payload.getAction())) {
                handler.handle(gitHub, payload, action);
                break;
            }
        }
    }
}
