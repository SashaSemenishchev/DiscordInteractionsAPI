package me.mrfunny.interactionapi.internal.cache;

import me.mrfunny.interactionapi.CommandManager;
import me.mrfunny.interactionapi.buttons.Button;
import me.mrfunny.interactionapi.menus.SelectMenu;
import me.mrfunny.interactionapi.modals.Modal;
import me.mrfunny.interactionapi.response.interfaces.CachedResponse;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

@SuppressWarnings("unchecked")
public class ResponseCache {
    public static final int DEFAULT_DELETE_AFTER = 300;
    private final static ArrayList<CachedResponse> responses = new ArrayList<>();
    private final static HashSet<CachedResponse> permanentResponses = new HashSet<>();
    private final static HashMap<Class<? extends CachedResponse>, BiPredicate<GenericInteractionCreateEvent, CachedResponse>> searchers = new HashMap<>();
    static {
        searchers.put(Modal.class, (interaction, gotResponse) -> {
            if(gotResponse instanceof Modal response) {
                return response.getCreatedFor().getIdLong() == interaction.getUser().getIdLong();
            }
            return false;
        });
        searchers.put(Button.class, (interaction, gotResponse) -> {
            if(gotResponse instanceof Button response) {
                return response.getCreatedFor().getIdLong() == interaction.getUser().getIdLong();
            }
            return false;
        });

        searchers.put(SelectMenu.class, (interaction, gotResponse) -> {
            if(gotResponse instanceof SelectMenu<?> response) {
                return response.getCreatedFor().getIdLong() == interaction.getUser().getIdLong();
            }
            return false;
        });
    }

    public static boolean decide(CachedResponse cachedResponse) {
        if((!cachedResponse.isPermanent() || cachedResponse.deleteAfter() != -1) && cachedResponse.getCreatedFor() != null) {
            responses.add(cachedResponse);
            CommandManager.getAsyncExecutor().schedule(() -> responses.remove(cachedResponse), cachedResponse.deleteAfter(), TimeUnit.SECONDS);
            return true;
        }
        permanentResponses.add(cachedResponse);
        return false;
    }

    public static void addCached(CachedResponse cachedResponse) {
        if(cachedResponse.isPermanent() && cachedResponse.deleteAfter() < 1) {
            throw new RuntimeException("Cannot cache a permanent response!");
        }
        int deleteTime = cachedResponse.deleteAfter();
        if(deleteTime < 1) {
            deleteTime = DEFAULT_DELETE_AFTER;
        }
        responses.add(cachedResponse);
        CommandManager.getAsyncExecutor().schedule(() -> responses.remove(cachedResponse), deleteTime, TimeUnit.SECONDS);
    }

    public static void addPermanent(CachedResponse response) {
        permanentResponses.add(response);
    }

    public static <T extends CachedResponse> T getCached(GenericInteractionCreateEvent event, Class<T> toSearch) {
        BiPredicate<GenericInteractionCreateEvent, CachedResponse> searcher = searchers.get(toSearch);
        if(searcher == null) return null;
        for(CachedResponse response : responses) {
            if(response.getCreatedFor() == null) continue;
            if(!Objects.equals(provideComponentApi(event.getInteraction()), response.getId())) continue;
            if(!searcher.test(event, response)) continue;
            return (T) response;
        }
        return null;
    }

    public static <T extends CachedResponse> T getPermanent(String id, Class<T> toReturn) {
        for(CachedResponse response : permanentResponses) {
            if(!toReturn.isAssignableFrom(response.getClass())) continue;
            if(response.getId() == null) {
                System.err.println(response.getClass().getName() + " has null ID");
                continue;
            }
            if(response.getId().equals(id)) {
                return (T) response;
            }
        }
        return null;
    }

    private static String provideComponentApi(Interaction interaction) {
        if(interaction instanceof ComponentInteraction c) return c.getComponentId();
        return null;
    }

    public static boolean removeCached(CachedResponse response) {
        return responses.remove(response);
    }

    /*
    This method never should be called outside the API, unless really needed. Modification can cause bugs
     */
    public static ArrayList<CachedResponse> getResponses() {
        return responses;
    }

    /*
    This method never should be called outside the API, unless really needed. Modification can cause bugs
     */
    public static HashSet<CachedResponse> getPermanentResponses() {
        return permanentResponses;
    }
}
