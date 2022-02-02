package com.willfp.eco.core.integrations.placeholder;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.willfp.eco.core.Eco;
import com.willfp.eco.core.EcoPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Class to handle placeholder integrations.
 */
public final class PlaceholderManager {
    /**
     * All registered placeholders.
     */
    private static final Map<EcoPlugin, Map<String, PlaceholderEntry>> REGISTERED_PLACEHOLDERS = new HashMap<>();

    /**
     * All registered placeholder integrations.
     */
    private static final Set<PlaceholderIntegration> REGISTERED_INTEGRATIONS = new HashSet<>();

    /**
     * Placeholder Cache.
     */
    private static final LoadingCache<EntryWithPlayer, String> PLACEHOLDER_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(50, TimeUnit.MILLISECONDS)
            .build(key -> key.entry.getResult(key.player));

    /**
     * Register a new placeholder integration.
     *
     * @param integration The {@link com.willfp.eco.core.integrations.placeholder.PlaceholderIntegration} to register.
     */
    public static void addIntegration(@NotNull final PlaceholderIntegration integration) {
        integration.registerIntegration();
        REGISTERED_INTEGRATIONS.add(integration);
    }

    /**
     * Register a placeholder.
     *
     * @param expansion The {@link com.willfp.eco.core.integrations.placeholder.PlaceholderEntry} to register.
     */
    public static void registerPlaceholder(@NotNull final PlaceholderEntry expansion) {
        EcoPlugin plugin = expansion.getPlugin() == null ? Eco.getHandler().getEcoPlugin() : expansion.getPlugin();
        Map<String, PlaceholderEntry> pluginPlaceholders = REGISTERED_PLACEHOLDERS
                .getOrDefault(plugin, new HashMap<>());
        pluginPlaceholders.put(expansion.getIdentifier(), expansion);
        REGISTERED_PLACEHOLDERS.put(plugin, pluginPlaceholders);
    }

    /**
     * Get the result of a placeholder with respect to a player.
     *
     * @param player     The player to get the result from.
     * @param identifier The placeholder identifier.
     * @return The value of the placeholder.
     * @deprecated Specify a plugin to get the result from.
     */
    @Deprecated
    public static String getResult(@Nullable final Player player,
                                   @NotNull final String identifier) {
        return getResult(player, identifier, null);
    }

    /**
     * Get the result of a placeholder with respect to a player.
     *
     * @param player     The player to get the result from.
     * @param identifier The placeholder identifier.
     * @param plugin     The plugin for the placeholder.
     * @return The value of the placeholder.
     */
    public static String getResult(@Nullable final Player player,
                                   @NotNull final String identifier,
                                   @Nullable final EcoPlugin plugin) {
        EcoPlugin owner = plugin == null ? Eco.getHandler().getEcoPlugin() : plugin;
        PlaceholderEntry entry = REGISTERED_PLACEHOLDERS.getOrDefault(owner, new HashMap<>()).get(identifier.toLowerCase());

        if (entry == null && plugin != null) {
            PlaceholderEntry alternate = REGISTERED_PLACEHOLDERS.getOrDefault(Eco.getHandler().getEcoPlugin(), new HashMap<>())
                    .get(identifier.toLowerCase());
            if (alternate != null) {
                entry = alternate;
            }
        }

        if (entry == null) {
            return "";
        }

        if (player == null && entry.requiresPlayer()) {
            return "";
        }

        return PLACEHOLDER_CACHE.get(new EntryWithPlayer(entry, player));
    }

    /**
     * Translate all placeholders with respect to a player.
     *
     * @param text   The text that may contain placeholders to translate.
     * @param player The player to translate the placeholders with respect to.
     * @return The text, translated.
     */
    public static String translatePlaceholders(@NotNull final String text,
                                               @Nullable final Player player) {
        String processed = text;
        for (PlaceholderIntegration integration : REGISTERED_INTEGRATIONS) {
            processed = integration.translate(processed, player);
        }
        return processed;
    }

    /**
     * Find all placeholders in a given text.
     *
     * @param text The text.
     * @return The placeholders.
     */
    public static List<String> findPlaceholdersIn(@NotNull final String text) {
        List<String> found = new ArrayList<>();
        for (PlaceholderIntegration integration : REGISTERED_INTEGRATIONS) {
            found.addAll(integration.findPlaceholdersIn(text));
        }

        return found;
    }

    private static record EntryWithPlayer(@NotNull PlaceholderEntry entry,
                                          @Nullable Player player) {

    }

    private PlaceholderManager() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}