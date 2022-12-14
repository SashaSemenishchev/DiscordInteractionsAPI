package me.mrfunny.interactionapi.internal.wrapper.resolver;

import me.mrfunny.interactionapi.commands.context.ContextCommand;
import me.mrfunny.interactionapi.commands.context.MessageContextCommand;
import me.mrfunny.interactionapi.commands.context.UserContextCommand;
import me.mrfunny.interactionapi.internal.wrapper.resolver.interfaces.Resolver;

import java.util.HashMap;

public class ContextCommandResolver implements Resolver {
    public ContextCommandResolver(
            ContextCommand<?> command,
            HashMap<String, UserContextCommand> contextUserCommands,
            HashMap<String, MessageContextCommand> contextMessageCommands
    ) {
        if(command instanceof UserContextCommand userCommand) {
            contextUserCommands.put(command.name(), userCommand);
        } else if(command instanceof MessageContextCommand contextCommand) {
            contextMessageCommands.put(command.name(), contextCommand);
        }
    }
}
