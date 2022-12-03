package me.mrfunny.interactionapi;

import me.mrfunny.interactionapi.commands.context.ContextCommand;
import me.mrfunny.interactionapi.commands.context.MessageContextCommand;
import me.mrfunny.interactionapi.commands.context.UserContextCommand;
import me.mrfunny.interactionapi.internal.Command;
import me.mrfunny.interactionapi.internal.wrapper.resolver.ContextCommandResolver;
import me.mrfunny.interactionapi.internal.wrapper.resolver.SlashCommandResolver;
import me.mrfunny.interactionapi.data.CommandExecutor;
import me.mrfunny.interactionapi.data.RegisteredCommand;
import me.mrfunny.interactionapi.internal.wrapper.JdaCommandWrapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.HashMap;

class CommandManagerImpl implements CommandManager {
    private final JDA jda;

    public CommandManagerImpl(JDA jda) {
        this.jda = jda;
    }

    private final HashMap<String, RegisteredCommand> slashCommands = new HashMap<>();
    private final HashMap<String, MessageContextCommand> messageContextCommands = new HashMap<>();
    private final HashMap<String, UserContextCommand> userContextCommands = new HashMap<>();

    @Override
    public void registerCommand(Command commandInstance) {
        if(commandInstance instanceof ContextCommand contextCommand) {
            new ContextCommandResolver(contextCommand, userContextCommands, messageContextCommands);
            return;
        }
        SlashCommandResolver resolver = new SlashCommandResolver(commandInstance);
        resolver.resolve();
        RegisteredCommand command = resolver.result();
        if(command == null) {
            throw new RuntimeException("Failed resolving command");
        }
        CommandData jdaCommand = JdaCommandWrapper.wrap(command);
        if(command.isGlobal()) {
            jda.upsertCommand(jdaCommand).queue();
        } else {
            for(Guild guild : jda.getGuilds()) {
                if(!command.getCommandBlueprint().shouldRegisterToGuild().apply(guild)) continue;
                guild.upsertCommand(jdaCommand).queue();
            }
        }
        slashCommands.put(command.getName(), command);
    }

    @Override
    public boolean processCommandInteraction(SlashCommandInteractionEvent event) {
        if(event == null) return false;
        RegisteredCommand command = slashCommands.get(event.getName());
        if(command == null) return false;

        if(event.getSubcommandName() != null) {
            CommandExecutor subcommand = command.getSubcommand(event.getSubcommandName());
            if(subcommand == null) return false;
            subcommand.execute(event);
            return true;
        }
        command.getMainExecutor().execute(event);
        return true;
    }

    @Override
    public boolean processContextInteraction(UserContextInteractionEvent event) {
        UserContextCommand command = userContextCommands.get(event.getCommandId());
        if(command == null) return false;
        command.execute(event.getUser());
        return true;
    }

    @Override
    public boolean processContextInteraction(MessageContextInteractionEvent event) {
        MessageContextCommand command = messageContextCommands.get(event.getCommandId());
        if(command == null) return false;
        command.execute();
        return true;
    }
}
