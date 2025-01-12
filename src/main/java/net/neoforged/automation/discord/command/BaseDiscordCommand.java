package net.neoforged.automation.discord.command;

import com.jagrosh.jdautilities.command.SlashCommand;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class BaseDiscordCommand extends SlashCommand {
    private final Map<String, Consumer<CommandAutoCompleteInteractionEvent>> autoComplete = new HashMap<>();

    protected void addAutoCompleteHandler(String option, Consumer<CommandAutoCompleteInteractionEvent> consumer) {
        autoComplete.put(option, consumer);
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        var option = autoComplete.get(event.getFocusedOption().getName());
        if (option != null) {
            option.accept(event);
        }
    }
}
