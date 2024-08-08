package net.neoforged.automation.runner;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class PRRunUtils {
    public static void setupPR(GHPullRequest pr, GitConsumer consumer) throws IOException, GitAPIException {
        var repo = Path.of("checkedoutprs/" + pr.getRepository().getFullName() + "/pr" + pr.getNumber() + "/" + new Random().nextInt(10000));
        Files.createDirectories(repo);
        try (var git = Git.init().setDirectory(repo.toFile()).call()) {
            git.remoteAdd().setName("origin").setUri(new URIish("https://github.com/" + pr.getHead().getRepository().getFullName() + ".git")).call();
            git.fetch().setRemote("origin").setRefSpecs("refs/heads/" + pr.getHead().getRef() + ":" + pr.getHead().getRef()).call();
            git.checkout().setName(pr.getHead().getRef()).call();

            consumer.run(repo, git);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        FileUtils.deleteDirectory(repo.toFile());
    }

    @FunctionalInterface
    public interface GitConsumer {
        void run(Path dir, Git git) throws IOException, GitAPIException;
    }
}
