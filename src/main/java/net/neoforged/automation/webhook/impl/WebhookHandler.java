package net.neoforged.automation.webhook.impl;

import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import net.neoforged.automation.StartupConfiguration;
import net.neoforged.automation.util.GHAction;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.io.MacInputStream;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;

// See https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#webhook-payload-object-common-properties
@SuppressWarnings({"rawtypes", "unchecked"})
public class WebhookHandler implements Handler {
    public static final String GITHUB_EVENT_HEADER = "X-GitHub-Event";
    public static final String GITHUB_SIGNATURE_HEADER = "X-Hub-Signature-256";
    public static final String GITHUB_DELIVERY = "X-GitHub-Delivery";
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookHandler.class);

    private final byte[] secretToken;
    private final GitHub gitHub;

    private final Map<GitHubEvent, EventHandler> handlers = new IdentityHashMap<>();

    public WebhookHandler(StartupConfiguration configuration, GitHub gitHub) {
        this.secretToken = configuration.get("webhookSecret", "").getBytes(StandardCharsets.UTF_8);
        this.gitHub = gitHub;
    }

    public WebhookHandler register(MultiEventHandler handler) {
        handler.register(this);
        return this;
    }

    public <T extends GHEventPayload> WebhookHandler registerHandler(GitHubEvent<T> event, EventHandler<T> handler) {
        handlers.compute(event, (k, old) -> {
            if (old != null) {
                return old.and(handler);
            }
            return handler;
        });
        return this;
    }

    public <T extends GHEventPayload> WebhookHandler registerFilteredHandler(GitHubEvent<T> event, ActionBasedHandler<T> handler, GHAction... actions) {
        return registerHandler(event, new ActionFilteredHandler<>(actions, handler));
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        final String event = ctx.header(GITHUB_EVENT_HEADER);
        if (event == null) {
            String response = "Missing " + GITHUB_EVENT_HEADER + " request header.\n";
            ctx.status(HttpStatus.BAD_REQUEST).result(response);
            return;
        }

        var ev = GitHubEvent.BY_NAME.get(event);
        if (ev == null) {
            ctx.status(HttpStatus.OK).result("Event unknown");
            return;
        }

        var handler = handlers.get(ev);
        if (handler == null) {
            ctx.status(HttpStatus.OK).result("No handlers registered for event " + event);
            return;
        }

        var bodyBytes = validateSignatures(ctx);
        try {
            handler.handle(gitHub, ev.parse(gitHub, bodyBytes));
        } catch (Exception exception) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).result("Failed to handle request: " + exception.getMessage());
            LOGGER.error("Failed to handle request {}: ", ctx.header(GITHUB_DELIVERY), exception);
        }
    }

    private byte[] validateSignatures(Context exchange) throws IOException {
        var mac = new HMac(new SHA256Digest());
        mac.init(new KeyParameter(secretToken));
        final String ghSignature = exchange.header(GITHUB_SIGNATURE_HEADER);
        var bytes = new MacInputStream(exchange.bodyInputStream(), mac).readAllBytes();
        var out = new byte[mac.getUnderlyingDigest().getDigestSize()];
        mac.doFinal(out, 0);
        compareSignatures(out, ghSignature);
        return bytes;
    }

    private void compareSignatures(byte[] bytes, String expected) {
        String actual = "sha256=" + bytesToHex(bytes);
        if (!actual.equals(expected)) {
            throw new ForbiddenResponse("Signatures do not match: expected '" + expected + "', actual '" + actual + "'");
        }
    }

    private static final byte[] HEX_ARRAY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }
}
