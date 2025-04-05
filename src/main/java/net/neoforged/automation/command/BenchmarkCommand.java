package net.neoforged.automation.command;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.Main;
import net.neoforged.automation.util.FunctionalInterfaces;
import org.intellij.lang.annotations.Language;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class BenchmarkCommand {
    public static void benchmark(GitHub gh, GHPullRequest pr, Configuration configuration,
                                        String against, String command,
                                        CommandProgressListener listener,
                                        FunctionalInterfaces.ConsumerException<String> onSuccess) throws IOException {
        AtomicReference<String> baseJson = new AtomicReference<>(),
                prJson = new AtomicReference<>();

        final FunctionalInterfaces.RunnableException checkComplete = () -> {
            var baseJ = baseJson.get();
            var prJ = prJson.get();
            if (baseJ != null && prJ != null) {
                listener.addStep("Uploading benchmarks...");

                var baseUrl = Main.fileHostService.upload(baseJ);
                var prUrl = Main.fileHostService.upload(prJ);

                var url = "https://jmh.morethan.io/?sources=" + URLEncoder.encode(baseUrl, StandardCharsets.UTF_8) + "," + URLEncoder.encode(prUrl, StandardCharsets.UTF_8);

                onSuccess.accept(url);
            }
        };

        listener.addStep("Running benchmark for base (`" + against + ")`...");
        runBenchmark(
                gh, pr, configuration,
                "base", against, command, listener,
                bj -> {
                    baseJson.set(bj);
                    checkComplete.run();
                }
        );

        listener.addStep("Running benchmark for PR...");
        runBenchmark(
                gh, pr, configuration,
                "head", pr.getHead().getSha(), command, listener,
                pj -> {
                    prJson.set(pj);
                    checkComplete.run();
                }
        );
    }

    public static void runBenchmark(GitHub gh, GHPullRequest pr, Configuration configuration,
                                    String type, String target, String command,
                                    CommandProgressListener listener,
                                    FunctionalInterfaces.ConsumerException<String> onSuccess) throws IOException {
        Main.actionRunner(gh, configuration.prActions())
                .name("Benchmark " + pr.getRepository().getFullName() + " #" + pr.getNumber() + ": " + type)
                .run(runner -> {
                    listener.addStep(type + ": Started action runner " + runner.getRun(gh).getHtmlUrl());

                    runner.git("init");
                    runner.clone(pr.getRepository().getHtmlUrl() + ".git", "origin", target);

                    runner.runCachingGradle(
                            pr,
                            () -> {
                                @Language("gradle") String init = """
allprojects {
    final replace = { List<String> args, String key, String arg ->
        while (args.contains(key)) {
            int idx = args.indexOf(key)
            args.remove(idx + 1)
            args.remove(idx)
        }

        args.addAll([key, arg])
    }

    afterEvaluate {
        tasks.matching { it.name == 'jmh' }.configureEach {
            if (it instanceof JavaExec) {
                var newArgs = new ArrayList<>(it.args)
                replace(newArgs, '-rff', rootProject.file('_benchmark.json').absolutePath)
                replace(newArgs, '-rf', 'json')
                it.setArgs(newArgs)
            }
        }
    }
}

""";
                                listener.addStep(type + ": Running benchmark using gradle task `" + command + "`...");
                                runner.writeFile("_benchmark_init.gradle", init);
                                runner.gradle("--init-script", "_benchmark_init.gradle", command);
                            }
                    );

                    listener.addStep(type + ": Benchmark finished.");

                    onSuccess.accept(new String(Objects.requireNonNull(runner.readFile("_benchmark.json")), StandardCharsets.UTF_8));
                })
                .onFailure(listener::handleFailure)
                .queue();
    }
}
