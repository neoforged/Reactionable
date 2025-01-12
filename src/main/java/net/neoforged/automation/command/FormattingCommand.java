package net.neoforged.automation.command;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.runner.PRActionRunner;
import net.neoforged.automation.runner.GitRunner;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

public class FormattingCommand {

    public static void run(GitHub gh, GHPullRequest pr, Configuration.PRActions actions, Configuration.RepoConfiguration repoConfiguration, String command, Consumer<GHWorkflowRun> onFailure, Runnable onSuccess) throws IOException {
        var command2 = command;
        var baseCommand = repoConfiguration.baseRunCommand();
        if (baseCommand.split("\n").length > 1) {
            var spl = baseCommand.split("\n", 2);
            baseCommand = spl[0].trim();
            command2 = spl[1].trim() + " && " + command2;
        }
        PRActionRunner.builder(pr)
                .upload(repoConfiguration.runUploadPattern())
                .command(baseCommand, command2)
                .onFailed((gitHub, run) -> onFailure.accept(run))
                .onFinished((gitHub, run, artifacts) -> {
                    GitRunner.setupPR(gh, pr, (dir, git, creds) -> {
                        List<String> deleted = new ArrayList<>();
                        try (var file = new ZipFile(artifacts.get("status").toFile())) {
                            var entry = file.entries().nextElement();
                            var status = new String(file.getInputStream(entry).readAllBytes());
                            for (String s : status.split("\n")) {
                                s = s.trim();
                                if (s.startsWith("deleted:")) {
                                    deleted.add(s.substring(8).trim());
                                }
                            }
                        }

                        try (var file = new ZipFile(artifacts.get("artifact").toFile())) {
                            var enm = file.entries();
                            while (enm.hasMoreElements()) {
                                var entry = enm.nextElement();
                                if (entry.isDirectory()) continue;
                                var path = dir.resolve(entry.getName());
                                if (!checkSafe(dir, path)) return;
                                Files.createDirectories(path.getParent());
                                Files.write(path, file.getInputStream(entry).readAllBytes());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        for (String del : deleted) {
                            var path = dir.resolve(del);
                            if (checkSafe(dir, path)) {
                                Files.delete(path);
                            }
                        }

                        git.add().addFilepattern(".").call();

                        git.commit().setCredentialsProvider(creds)
                                .setCommitter(creds.getPerson())
                                .setMessage("Update formatting")
                                .setSign(false)
                                .setNoVerify(true)
                                .call();
                        git.push().setRemote("origin").setRefSpecs(new RefSpec("HEAD:refs/heads/" + pr.getHead().getRef())).setCredentialsProvider(creds).call();
                        onSuccess.run();
                    });
                })
                .build()
                .queue(gh, actions);
    }

    private static boolean checkSafe(Path dir, Path path) {
        var parent = path;
        while ((parent = parent.getParent()) != null) {
            if (parent.getFileName().toString().equals(".git")) return false;
            if (dir.equals(parent)) return true;
        }
        return false;
    }
}
