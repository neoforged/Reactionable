package net.neoforged.automation.runner;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class GitRunner {
    private static final Random RANDOM = new Random();

    public static void setupPR(GitHub gitHub, GHPullRequest pr, GitConsumer consumer) throws IOException, GitAPIException {
        var repo = Path.of("checkout/prs/" + pr.getRepository().getFullName() + "/pr" + pr.getNumber() + "/" + nextInt());
        setupRepo(repo, gitHub, pr.getHead().getRepository(), pr.getHead().getRef(), consumer);
    }

    public static void setupRepo(GitHub gitHub, GHRepository repo, String head, GitConsumer consumer) throws IOException, GitAPIException {
        var path = Path.of("checkout/repos/" + repo.getFullName() + "/" + nextInt());
        setupRepo(path, gitHub, repo, head, consumer);
    }

    public static void setupRepo(Path path, GitHub gitHub, GHRepository repo, String head, GitConsumer consumer) throws IOException, GitAPIException {
        Files.createDirectories(path);
        try (var git = Git.cloneRepository()
                .setURI("https://github.com/" + repo.getFullName() + ".git")
                .setBranch("refs/heads/" + head)
                .setDirectory(path.toFile()).call()) {
            var botName = GitHubAccessor.getApp(gitHub).getSlug() + "[bot]";
            var user = gitHub.getUser(botName);
            var creds = new BotCredentialsProvider(
                    botName,
                    GitHubAccessor.getToken(gitHub),
                    user
            );

            consumer.run(path, git, creds);

            git.getRepository().close();
        }

        FileUtils.delete(path.toFile(), FileUtils.RECURSIVE);
    }

    private static synchronized int nextInt() {
        return RANDOM.nextInt(10000);
    }

    @FunctionalInterface
    public interface GitConsumer {
        void run(Path dir, Git git, BotCredentialsProvider creds) throws IOException, GitAPIException;
    }

    public static class BotCredentialsProvider extends UsernamePasswordCredentialsProvider {
        private final GHUser bot;
        public BotCredentialsProvider(String username, String password, GHUser bot) {
            super(username, password);
            this.bot = bot;
        }

        public String getUser() {
            return bot.getLogin();
        }

        public String getEmail() {
            return bot.getId() + "+" + bot.getLogin() + "@users.noreply.github.com";
        }

        public PersonIdent getPerson() {
            return new PersonIdent(getUser(), getEmail());
        }
    }
}
