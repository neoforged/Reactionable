package net.neoforged.automation.discord.command;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.neoforged.automation.discord.DiscordBot;
import net.neoforged.automation.runner.GitRunner;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.github.GitHub;

import java.util.List;

public class GitHubCommand extends BaseDiscordCommand {
    public GitHubCommand(GitHub gitHub) {
        this.name = "github";
        this.help = "GitHub automation commands";

        this.children = new SlashCommand[] {
                new PushTag(gitHub)
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
        protected void execute(SlashCommandEvent event, String githubUser) throws Exception {
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
                        git.commit()
                                .setCredentialsProvider(creds)
                                .setCommitter(creds.getPerson())
                                .setMessage(event.optString("message"))
                                .setSign(false)
                                .setNoVerify(true)
                                .call();

                        git.tag()
                                .setCredentialsProvider(creds)
                                .setSigned(false)
                                .setName(tag)
                                .setTagger(creds.getPerson())
                                .setAnnotated(true)
                                .call();

                        git.push()
                                .setCredentialsProvider(creds)
                                .setRemote("origin")
                                .setRefSpecs(new RefSpec(branch), new RefSpec(tag))
                                .call();
                    }
            );

            event.getHook().sendMessage("Pushed tag `" + tag + "` to branch `" + branch + "`.").queue();
        }
    }
}
