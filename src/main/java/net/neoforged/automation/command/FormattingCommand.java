package net.neoforged.automation.command;

import net.neoforged.automation.Configuration;
import net.neoforged.automation.runner.PRActionRunner;
import net.neoforged.automation.runner.PRRunUtils;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

public class FormattingCommand {

    public static void run(GitHub gh, GHPullRequest pr, Configuration.PRActions actions, Configuration.RepoConfiguration repoConfiguration, String command, Consumer<GHWorkflowRun> onFailure, Runnable onSuccess) throws IOException {
        PRActionRunner.builder(pr)
                .upload(repoConfiguration.runUploadPattern())
                .command(repoConfiguration.baseRunCommand(), command)
                .onFailed((gitHub, run) -> onFailure.accept(run))
                .onFinished((gitHub, run, artifact) -> {
                    PRRunUtils.setupPR(pr, (dir, git) -> {
                        try (var file = new ZipFile(artifact.toFile())) {
                            var enm = file.entries();
                            while (enm.hasMoreElements()) {
                                var entry = enm.nextElement();
                                Files.write(dir.resolve(entry.getName()), file.getInputStream(entry).readAllBytes());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        git.add().addFilepattern(".").call();

                        var botName = gitHub.getApp().getSlug() + "[bot]";
                        var user = gitHub.getUser(botName);
                        var creds = new UsernamePasswordCredentialsProvider(
                            botName,
                            GitHubAccessor.getToken(gitHub)
                        );

                        git.commit().setCredentialsProvider(creds)
                                .setCommitter(botName, user.getId() + "+" + botName + "@users.noreply.github.com")
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
}
