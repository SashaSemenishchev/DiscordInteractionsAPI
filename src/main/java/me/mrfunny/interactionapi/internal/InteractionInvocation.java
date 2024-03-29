package me.mrfunny.interactionapi.internal;

import me.mrfunny.interactionapi.internal.wrapper.util.ResponseMapper;
import me.mrfunny.interactionapi.response.MessageContent;
import me.mrfunny.interactionapi.modals.Modal;
import me.mrfunny.interactionapi.response.interfaces.InteractionResponse;
import me.mrfunny.interactionapi.util.ConsumerUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class InteractionInvocation {
    protected boolean deferred = false;
    protected final IReplyCallback interaction;
    protected InteractionHook interactionHook = null;
    protected Message possibleMessage = null;
    protected boolean replied = false;
    protected boolean ephemeral = false;

    public InteractionInvocation(IReplyCallback replyCallback) {
        this.interaction = replyCallback;
    }
    
    public void deferAsync() {
        this.deferAsync(ephemeral, null);
    }
    
    public void deferAsync(boolean ephemeral) {
        this.deferAsync(ephemeral, null);
    }

    
    public void deferAsync(Consumer<InteractionInvocation> onComplete) {
        this.deferAsync(ephemeral, onComplete);
    }

    public void deferAsync(boolean ephemeral, Consumer<InteractionInvocation> onComplete) {
        if(deferred) return;
        this.createDefer(ephemeral).queue(hook -> {
            this.deferred = true;
            this.interactionHook = hook;
            if(onComplete != null) {
                onComplete.accept(this);
            }
        });
    }
    
    public void sendAsync(InteractionResponse response, boolean ephemeral, Consumer<InteractionInvocation> messageConsumer) {
        if(replied) {
            Logger.getGlobal().warning("The interaction is already sent, doing nothing.");
            return;
        }
        if(response instanceof MessageContent messageContent) {
            if(deferred) {
                ResponseMapper.mapSend(messageContent, this.interactionHook).queue(message -> {
                    this.replied = true;
                    this.possibleMessage = message;
                    ConsumerUtil.accept(messageConsumer, this);
                });
                return;
            }
            createSend(messageContent, ephemeral).queue(hook -> {
                setInteractionHook(hook);
                ConsumerUtil.accept(messageConsumer, this);
            });
        } else if(
                response instanceof Modal modal &&
                        interaction instanceof GenericCommandInteractionEvent commandInteraction
        ) {
            if(deferred) {
                throw new RuntimeException("Defer reply does not support modals");
            } else if(ephemeral) {
                throw new IllegalArgumentException("Modals can't be ephemeral");
            }
            try {
                createModalResponse(commandInteraction, modal).queue(s -> ConsumerUtil.accept(messageConsumer, this));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public void sendAsync(InteractionResponse response, boolean ephemeral) {
        this.sendAsync(response, ephemeral, null);
    }

    public void sendAsync(InteractionResponse response, Consumer<InteractionInvocation> consumer) {
        this.sendAsync(response, ephemeral, consumer);
    }
    
    public void sendAsync(InteractionResponse response) {
        this.sendAsync(response, ephemeral, null);
    }
    
    public void editAsync(MessageContent messageContent, Consumer<Message> consumer) {
        createEdit(messageContent).queue(consumer);
    }
    
    public InteractionInvocation defer() {
        return this.defer(ephemeral);
    }

    public InteractionInvocation defer(boolean ephemeral) {
        if(deferred) return this;
        this.interactionHook = createDefer(ephemeral).complete();
        this.deferred = true;
        return this;
    }

    public InteractionInvocation send(InteractionResponse response, boolean ephemeral) {
        if(replied) {
            Logger.getGlobal().warning("The interaction is already sent, doing nothing.");
            return this;
        }
        if(response instanceof MessageContent messageContent) {
            if(deferred) {
                this.possibleMessage = ResponseMapper.mapSend(messageContent, this.interactionHook).complete();
                this.replied = true;
                return this;
            }
            setInteractionHook(createSend(messageContent, ephemeral).complete());
        } else if(
                response instanceof Modal modal &&
                        interaction instanceof IModalCallback commandInteraction
        ) {
            if(deferred) {
                throw new RuntimeException("Defer reply does not support modals");
            } else if(ephemeral) {
                throw new IllegalArgumentException("Modals can't be ephemeral");
            }
            try {
                createModalResponse(commandInteraction, modal).complete();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        return this;
    }

    protected ModalCallbackAction createModalResponse(IModalCallback event, Modal modal) throws Exception {
        return event.replyModal(modal.getMappedModal());
    }

    
    public InteractionInvocation send(InteractionResponse response) {
        return this.send(response, ephemeral);
    }

    
    public InteractionInvocation send(Modal response) {
        return this.send(response, ephemeral);
    }

    
    public Message edit(MessageContent newContent) {
        return createEdit(newContent).complete();
    }

    
    @NotNull
    public ReplyCallbackAction createSend(MessageContent content, boolean ephemeral) {
        ReplyCallbackAction callbackAction = interaction.deferReply(ephemeral);
        ResponseMapper.map(content, callbackAction);
        return callbackAction;
    }
    
    public WebhookMessageEditAction<Message> createEdit(MessageContent newContent) {
        if(!replied && !deferred) {
            throw new RuntimeException("The interaction can't be edited while not being replied");
        }

        return ResponseMapper.mapEdit(newContent, this.interactionHook, this.possibleMessage.getId());
    }

    
    public ReplyCallbackAction createDefer(boolean ephemeral) {
        return this.interaction.deferReply(ephemeral);
    }

    public User getUser() {
        return this.interaction.getUser();
    }

    
    public Member getMember() {
        return this.interaction.getMember();
    }

    public OffsetDateTime getTimeCreated() {
        return this.interaction.getTimeCreated();
    }

    public ChannelType getChannelType() {
        return this.interaction.getChannelType();
    }
    
    public Guild getGuild() {
        return this.interaction.getGuild();
    }
    
    public void setInteractionHook(InteractionHook interactionHook) {
        this.replied = true;
        this.interactionHook = interactionHook;
    }

    public InteractionInvocation ephemeral(boolean value) {
        this.ephemeral = value;
        return this;
    }

    public InteractionHook getInteractionHook() {
        return interactionHook;
    }
    
    public Message getPossibleMessage() {
        return possibleMessage;
    }

    public IReplyCallback getInteraction() {
        return interaction;
    }
}
