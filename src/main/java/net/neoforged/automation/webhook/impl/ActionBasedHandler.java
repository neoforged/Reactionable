package net.neoforged.automation.webhook.impl;

import net.neoforged.automation.util.GHAction;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;

import java.io.IOException;

public interface ActionBasedHandler<T extends GHEventPayload> {
    void handle(GitHub gitHub, T payload, GHAction action) throws Exception;
}
