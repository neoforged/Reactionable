package net.neoforged.automation.runner;

import io.javalin.websocket.WsConfig;
import net.neoforged.automation.Configuration;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public final class ActionRunnerHandler implements Consumer<WsConfig> {
    private record PendingConfiguration(RunnerAction runner, ActionExceptionHandler failure) {}
    private final Map<String, PendingConfiguration> pending = new HashMap<>();
    private final Map<String, ActionRunner> running = new HashMap<>();
    private final String wsBaseUrl;
    private final ExecutorService executor;

    public ActionRunnerHandler(String wsBaseUrl, ExecutorService executor) {
        this.wsBaseUrl = wsBaseUrl;
        this.executor = executor;
    }

    public Builder builder(GitHub gitHub, Configuration.PRActions config) {
        return new Builder(gitHub, config);
    }

    public void queue(GitHub gitHub, Configuration.PRActions config, RunnerOS os, RunnerAction consumer, ActionExceptionHandler failure) throws IOException {
        var id = UUID.randomUUID().toString();
        var repo = gitHub.getRepository(config.repository());
        var spl = config.workflow().split("@");
        repo.getWorkflow(spl[0])
                .dispatch(spl[1], Map.of(
                        "endpoint", wsBaseUrl.replace("<id>", id),
                        "os", os.latest
                ));
        this.pending.put(id, new PendingConfiguration(consumer, failure));
    }

    public void close(ActionRunner runner, boolean forceShutdown) {
        running.remove(runner.context.pathParam("id"));
        if (forceShutdown && runner.execution != null && !runner.execution.isDone()) {
            runner.execution.cancel(true);
        }
    }

    @Override
    public void accept(WsConfig wsConfig) {
        wsConfig.onConnect(wsConnectContext -> {
            var id = wsConnectContext.pathParam("id");
            var cons = pending.remove(id);
            if (cons == null) {
                wsConnectContext.closeSession();
                return;
            }

            wsConnectContext.session.setIdleTimeout(Duration.ofSeconds(0));

            var runner = new ActionRunner(wsConnectContext, this);
            running.put(id, runner);
            runner.queue(executor, cons.runner, cons.failure);
        });

        wsConfig.onMessage(wsMessageContext -> {
            var id = wsMessageContext.pathParam("id");
            var runner = running.get(id);
            if (runner == null) {
                wsMessageContext.closeSession();
                return;
            }
            runner.acceptMessage(wsMessageContext);
        });

        wsConfig.onClose(ctx -> {
            var id = ctx.pathParam("id");
            var runner = running.get(id);
            if (runner != null) {
                close(runner, true);
            }
        });
    }

    public class Builder {
        private final GitHub gitHub;
        private final Configuration.PRActions config;
        private RunnerOS os = RunnerOS.UBUNTU;

        private RunnerAction runner;
        private ActionExceptionHandler onFailure;

        public Builder(GitHub gitHub, Configuration.PRActions config) {
            this.gitHub = gitHub;
            this.config = config;
        }

        public Builder os(RunnerOS os) {
            this.os = os;
            return this;
        }

        public Builder run(RunnerAction cons) {
            if (this.runner != null) {
                this.runner = runner.andThen(cons);
            } else {
                this.runner = cons;
            }
            return this;
        }

        public Builder onFailure(ActionExceptionHandler onFailure) {
            if (this.onFailure != null) {
                this.onFailure = this.onFailure.andThen(onFailure);
            } else {
                this.onFailure = onFailure;
            }
            return this;
        }

        public void queue() throws IOException {
            ActionRunnerHandler.this.queue(gitHub, config, os, runner, onFailure);
        }
    }
}
