package net.neoforged.automation.webhook.label;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.Map;

public interface LabelHandler {
    Map<String, Class<? extends LabelHandler>> TYPES = Map.of(
            "lock", LockLabelHandler.class,
            "merge", MergeLabelHandler.class,
            "keep-rebased", KeepRebasedHandler.class
    );

    default void onLabelAdded(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {

    }

    default void onLabelRemoved(GitHub gitHub, GHUser actor, GHIssue issue, GHLabel label) throws Exception {

    }

    final class Deserializer extends JsonDeserializer<LabelHandler> {
        @Override
        public LabelHandler deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            var node = p.readValueAs(JsonNode.class);
            var type = TYPES.get(node.get("type").asText());
            ((ObjectNode)node).remove("type");
            return ctxt.readTreeAsValue(node, type);
        }
    }
}
