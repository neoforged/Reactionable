package net.neoforged.automation.discord.command;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.automation.discord.DiscordBot;
import net.neoforged.automation.runner.GitRunner;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.github.GHCache;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public class GitHubCommand extends BaseDiscordCommand {
    public GitHubCommand(GitHub gitHub) {
        this.name = "github";
        this.help = "GitHub automation commands";

        this.children = new SlashCommand[] {
                new PushTag(gitHub),
                new PurgeCaches(gitHub)
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    private static class PushTag extends GitHubLinkedCommand {

        private PushTag(GitHub gitHub) {
            super(gitHub);
            this.name = "push-tag";
            this.help = "Push a tagged commit to a repo";

            this.options = List.of(
                    new OptionData(OptionType.STRING, "repo", "The repository to push to", true).setAutoComplete(true),
                    new OptionData(OptionType.STRING, "tag", "The tag to push", true),
                    new OptionData(OptionType.STRING, "message", "The commit message", true),
                    new OptionData(OptionType.STRING, "branch", "The branch to push to", false).setAutoComplete(true)
            );

            addAutoCompleteHandler("repo", DiscordBot::suggestRepositories);
            addAutoCompleteHandler("branch", DiscordBot.suggestBranches(gitHub));
        }

        @Override
        protected void execute(SlashCommandEvent event, GHUser githubUser) throws Exception {
            var repo = gitHub.getRepository(event.optString("repo"));

            checkUserAccess(repo, githubUser);

            var tag = event.optString("tag");
            var branch = event.optString("branch", repo.getDefaultBranch());

            event.deferReply().queue();

            GitRunner.setupRepo(
                    gitHub,
                    repo,
                    branch,
                    (dir, git, creds) -> {
                        var userEmail = githubUser.getId() + "+" + githubUser.getLogin() + "@users.noreply.github.com";

                        git.commit()
                                .setCredentialsProvider(creds)
                                .setCommitter(creds.getPerson())
                                .setMessage(event.optString("message") + "\n\nCo-authored-by: " + githubUser.getLogin() + " <" + userEmail + ">")
                                .setSign(false)
                                .setNoVerify(true)
                                .call();

                        git.tag()
                                .setCredentialsProvider(creds)
                                .setSigned(false)
                                .setName(tag)
                                .setTagger(new PersonIdent(githubUser.getLogin(), userEmail))
                                .setAnnotated(true)
                                .call();

                        git.push()
                                .setCredentialsProvider(creds)
                                .setRemote("origin")
                                .setRefSpecs(new RefSpec(tag))
                                .call();

                        git.push()
                                .setCredentialsProvider(creds)
                                .setRemote("origin")
                                .setRefSpecs(new RefSpec(branch))
                                .call();
                    }
            );

            event.getHook().sendMessage("Pushed tag `" + tag + "` to branch `" + branch + "`.").queue();
        }
    }

    private static class PurgeCaches extends GitHubLinkedCommand {

        private PurgeCaches(GitHub gitHub) {
            super(gitHub);
            this.name = "purge-caches";
            this.help = "Purges action caches of a repository";

            this.options = List.of(
                    new OptionData(OptionType.STRING, "repo", "The repository whose caches to purge", true).setAutoComplete(true),
                    new OptionData(OptionType.STRING, "key", "Key regex that determines whether a cache should be purged", false),
                    new OptionData(OptionType.STRING, "ref", "Ref regex that determines whether a cache should be purged (starts with refs/heads/ or refs/pull/)", false)
            );

            addAutoCompleteHandler("repo", DiscordBot::suggestRepositories);
        }

        @Override
        protected void execute(SlashCommandEvent event, GHUser githubUser) throws Exception {
            var repo = gitHub.getRepository(event.optString("repo"));

            checkUserAccess(repo, githubUser);

            event.deferReply().queue();

            var pattern = Pattern.compile(event.optString("key", ".*")).asMatchPredicate();
            var refPattern = Pattern.compile(event.optString("ref", ".*")).asMatchPredicate();

            var caches = GitHubAccessor.getCaches(repo).toList();
            int deleted = 0;

            for (GHCache ghCache : caches) {
                if (pattern.test(ghCache.getKey()) && refPattern.test(ghCache.getRef())) {
                    try {
                        ghCache.delete();
                        deleted++;
                    } catch (IOException ignore) {
                        // Sometimes GitHub may delete older caches before we have a chance to
                    }
                }
            }

            event.getHook().sendMessage("Deleted %s out of %s caches.".formatted(deleted, caches.size())).queue();
        }
    }
}
