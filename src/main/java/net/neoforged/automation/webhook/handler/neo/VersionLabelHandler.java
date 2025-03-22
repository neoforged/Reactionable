package net.neoforged.automation.webhook.handler.neo;

import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.webhook.impl.ActionBasedHandler;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.net.URI;
import java.util.Properties;
import java.util.stream.Collectors;

public class VersionLabelHandler implements ActionBasedHandler<GHEventPayload.PullRequest> {
    @Override
    public void handle(GitHub gitHub, GHEventPayload.PullRequest payload, GHAction action) throws Exception {
        var head = payload.getPullRequest().getHead();
        var props = new Properties();
        var urlLoc = "https://raw.githubusercontent.com/%s/refs/heads/%s/gradle.properties".formatted(head.getRepository().getFullName(), head.getRef());
        try (var str = URI.create(urlLoc).toURL().openStream()) {
            props.load(str);
        }

        var mcVersionProp = props.get("minecraft_version");

        if (mcVersionProp != null) {
            var mcVer = mcVersionProp.toString();

            var toRemoveLabels = payload.getPullRequest().getLabels().stream()
                    .map(GHLabel::getName)
                    .filter(name -> name.matches("\\d+\\.\\d+(\\.\\d+)?"))
                    .collect(Collectors.toList());

            if (!toRemoveLabels.remove(mcVer)) {
                // Only add the label if it exists, don't create a new one
                if (GitHubAccessor.getLabel(payload.getRepository(), mcVer) != null) {
                    payload.getPullRequest().addLabels(mcVer);
                }
            }

            if (!toRemoveLabels.isEmpty()) {
               payload.getPullRequest().removeLabels(toRemoveLabels.toArray(String[]::new));
            }
        }
    }
}
