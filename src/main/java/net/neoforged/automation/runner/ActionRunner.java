package net.neoforged.automation.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.websocket.WsCloseStatus;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import net.neoforged.automation.Main;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jgit.util.Base64;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class ActionRunner {
    Future<?> execution;

    private final WsContext context;

    private String repository;
    private long runId;

    private volatile CompletableFuture<ObjectNode> nextMessage;

    public ActionRunner(WsContext context) {
        this.context = context;
    }

    void queue(ExecutorService executor, RunnerAction cons, ActionExceptionHandler onFailure) {
        execution = executor.<Object>submit(() -> {
            try {
                requestDetails();
                cons.run(this);
            } catch (Exception exception) {
                Main.LOGGER.error("Action runner failed with exception: ", exception);
                onFailure.accept(this, exception.getMessage());
                context.closeSession(WsCloseStatus.SERVER_ERROR, "caught error");
            }
            return null;
        });
    }

    private void requestDetails() {
        var node = sendAndExpect("details");
        repository = node.get("repository").asText();
        runId = node.get("id").asLong();
    }

    public GHWorkflowRun getRun(GitHub gitHub) throws IOException {
        return gitHub.getRepository(repository).getWorkflowRun(runId);
    }

    public String git(String... command) {
        return exec(ArrayUtils.addFirst(command, "git"));
    }
    
    public void gradle(String... command) {
        exec(ArrayUtils.addFirst(command, "./gradlew"));
    }

    public void clone(String url, String originName, String ref) {
        git("remote", "add", originName, url);
        git("fetch", originName, ref + ":temp", "--tags");
        git("checkout", "temp");
    }

    public String diff() {
        git("add", ".");
        return git("diff", "--cached");
    }

    public void writeFile(String path, String content) {
        sendAndExpect("write-file", node -> {
            node.put("path", path);
            node.put("content", content);
        });
    }

    public byte @Nullable [] readFile(String path) {
        var json = sendAndExpect("read-file", node -> {
            node.put("path", path);
        });

        if (json.has("file")) {
            return Base64.decode(json.get("file").asText());
        }

        return null;
    }

    public String exec(String... command) {
        var res = sendAndExpect("command", node -> {
            var arr = node.putArray("command");
            for (String cmd : command) {
                arr.add(cmd);
            }
        });
        if (res.has("stderr")) {
            throw new ExecutionException("Command '" + String.join(" ", command) + "' failed execution with error: " + res.get("stderr").asText());
        }
        return res.get("stdout").asText();
    }

    public void log(String message) {
        sendAndExpect("log", n -> n.put("message", message));
    }

    public void close() {
        context.closeSession(WsCloseStatus.NORMAL_CLOSURE, "actions executed");
    }

    private ObjectNode sendAndExpect(String type) {
        return sendAndExpect(type, n -> {});
    }

    private ObjectNode sendAndExpect(String type, Consumer<ObjectNode> cons) {
        nextMessage = new CompletableFuture<>();
        var node = Main.JSON.createObjectNode();
        node.put("type", type);
        cons.accept(node);
        context.send(node.toPrettyString());
        return nextMessage.join();
    }

    public void acceptMessage(WsMessageContext context) throws JsonProcessingException {
        if (nextMessage != null) {
            nextMessage.complete(Main.JSON.readValue(context.message(), ObjectNode.class));
            nextMessage = null;
        }
    }

    private static class ExecutionException extends RuntimeException {
        private ExecutionException(String msg) {
            super(msg);
        }
    }
}
