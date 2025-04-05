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

public class BenchmarkCommand {
    public static void benchmark(GitHub gh, GHPullRequest pr, Configuration configuration,
                                        String against, String command,
                                        CommandProgressListener listener,
                                        FunctionalInterfaces.ConsumerException<String> onSuccess) throws IOException {
        listener.addStep("Running benchmark for base (`" + against + ")`...");
        runBenchmark(
                gh, pr, configuration,
                "base", against, command, listener,
                baseJson -> {
                    listener.addStep("Running benchmark for PR...");
                    runBenchmark(
                            gh, pr, configuration,
                            "head", pr.getHead().getSha(), command, listener,
                            prJson -> {
                                listener.addStep("Uploading benchmarks...");

                                var baseUrl = Main.fileHostService.upload(baseJson);
                                var prUrl = Main.fileHostService.upload(prJson);

                                var url = "https://jmh.morethan.io/?sources=" + URLEncoder.encode(baseUrl, StandardCharsets.UTF_8) + "," + URLEncoder.encode(prUrl, StandardCharsets.UTF_8);

                                onSuccess.accept(url);
                            }
                    );
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
                    listener.addStep("Started action runner " + runner.getRun(gh).getHtmlUrl());

                    runner.git("init");
                    runner.clone(pr.getRepository().getHtmlUrl() + ".git", "origin", target);

                    runner.runCaching(
                            "gradle-pr-" + pr.getRepository().getFullName() + "-" + pr.getNumber() + "-",
                            runner.resolveHome(".gradle/"),
                            System.currentTimeMillis() / 1000,
                            () -> {
                                @Language("gradle") String init = """
import org.gradle.api.tasks.JavaExec

allprojects {
    final replace = { JavaExec task, String key, String arg ->
        while (task.args.contains(key)) {
            int idx = task.args.indexOf(key)
            task.args.remove(idx + 1)
            task.args.remove(idx)
        }

        task.args.addAll([key, arg])
    }

    afterEvaluate {
        tasks.matching { it.name == 'jmh' }.configureEach {
            if (it instanceof JavaExec) {
                replace(it, '-rff', rootProject.file('_benchmark.json').absolutePath)
                replace(it, '-rf', 'json')
            }
        }
    }
}
""";
                                listener.addStep("Running benchmark using gradle task `" + command + "`...");
                                runner.writeFile("_benchmark_init.gradle", init);
                                runner.gradle("--init-script", "_benchmark_init.gradle", command);
                            }
                    );

                    listener.addStep("Benchmark for " + type + " finished.");

                    onSuccess.accept(new String(Objects.requireNonNull(runner.readFile("_benchmark.json")), StandardCharsets.UTF_8));
                })
                .onFailure(listener::handleFailure)
                .queue();
    }
}
