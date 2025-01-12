package net.neoforged.automation.webhook.handler;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.impl.GitHubEvent;
import net.neoforged.automation.webhook.impl.MultiEventHandler;
import net.neoforged.automation.webhook.impl.WebhookHandler;
import net.neoforged.automation.webhook.label.LabelHandler;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;

public final class LabelEventHandler implements MultiEventHandler {
    @Override
    public void register(WebhookHandler web) {
        web.registerFilteredHandler(GitHubEvent.ISSUES, (gitHub, payload, action) -> {
            var handler = getHandler(payload, payload.getLabel());
            if (handler == null) return;
            switch (action) {
                case LABELED -> handler.onLabelAdded(gitHub, payload.getSender(), payload.getIssue(), payload.getLabel());
                case UNLABELED -> handler.onLabelRemoved(gitHub, payload.getSender(), payload.getIssue(), payload.getLabel());
            }
        }, GHAction.LABELED, GHAction.UNLABELED);

        web.registerFilteredHandler(GitHubEvent.PULL_REQUEST, (gitHub, payload, action) -> {
            var handler = getHandler(payload, payload.getLabel());
            if (handler == null) return;
            switch (action) {
                case LABELED -> handler.onLabelAdded(gitHub, payload.getSender(), payload.getPullRequest(), payload.getLabel());
                case UNLABELED -> handler.onLabelRemoved(gitHub, payload.getSender(), payload.getPullRequest(), payload.getLabel());
            }
        }, GHAction.LABELED, GHAction.UNLABELED);
        
        web.registerFilteredHandler(GitHubEvent.PULL_REQUEST, (gitHub, payload, action) -> {
            var pr = payload.getPullRequest();

            var config = Configuration.get(payload.getRepository()).labelHandlers();

            for (GHLabel label : pr.getLabels()) {
                var handler = config.get(label.getName());
                if (handler != null) {
                    handler.onSynchronized(gitHub, pr, label);
                }
            }
        }, GHAction.SYNCHRONIZE);
    }

    @Nullable
    private static LabelHandler getHandler(GHEventPayload payload, GHLabel label) {
        return Configuration.get(payload.getRepository())
                .labelHandlers().get(label.getName());
    }
}
