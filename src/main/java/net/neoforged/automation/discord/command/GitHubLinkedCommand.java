package net.neoforged.automation.discord.command;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.neoforged.automation.db.Database;
import net.neoforged.automation.db.DiscordUsersDAO;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

public abstract class GitHubLinkedCommand extends BaseDiscordCommand {
    protected final GitHub gitHub;

    protected GitHubLinkedCommand(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        var github = Database.withExtension(DiscordUsersDAO.class, db -> db.getUser(event.getUser().getIdLong()));
        if (github == null) {
            event.reply("Please link your GitHub account first.").queue();
            return;
        }
        try {
            execute(event, github);
        } catch (ValidationException v) {
            event.getHook().sendMessage(v.getMessage()).queue();
        } catch (Exception ex) {
            event.getHook().sendMessage("Failed: " + ex.getMessage()).queue();
        }
    }

    protected abstract void execute(SlashCommandEvent event, String githubUser) throws Exception;

    protected void checkUserAccess(GHRepository repo, String githubUser) throws Exception {
        if (!repo.hasPermission(githubUser, GHPermissionType.WRITE)) {
            throw new ValidationException("You cannot push to this repo!");
        }
    }

    protected static class ValidationException extends Exception {
        public ValidationException(String msg) {
            super(msg);
        }
    }
}
