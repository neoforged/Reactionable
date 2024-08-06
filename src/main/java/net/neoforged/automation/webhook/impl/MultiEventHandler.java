package net.neoforged.automation.webhook.impl;

public interface MultiEventHandler {
    void register(WebhookHandler handler);
}
