package net.neoforged.automation.discord;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class GHLinkCommand extends SlashCommand {
    private final String clientId;
    private final String redirectUrl;
    public GHLinkCommand(String clientId, String redirectUrl) {
        this.name = "ghlink";
        this.help = "Link your Discord account to your GitHub account";

        this.clientId = clientId;
        this.redirectUrl = redirectUrl;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        var url = "https://github.com/login/oauth/authorize?client_id=" + clientId + "&response_type=code&scope=user:read&redirect_uri=" + redirectUrl + "&state=" + event.getUser().getId();

        event.reply("Please use the button below to link this Discord account to a GitHub one.")
                .setEphemeral(true)
                .addActionRow(Button.link(url, "Link account"))
                .queue();
    }
}
