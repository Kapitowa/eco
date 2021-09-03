package com.willfp.eco.core.items;

import com.willfp.eco.core.items.args.LookupArgParser;
import com.willfp.eco.core.recipe.parts.EmptyTestableItem;
import com.willfp.eco.core.recipe.parts.MaterialTestableItem;
import com.willfp.eco.core.recipe.parts.ModifiedTestableItem;
import com.willfp.eco.core.recipe.parts.TestableStack;
import com.willfp.eco.util.NamespacedKeyUtils;
import lombok.experimental.UtilityClass;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Class to manage all custom and vanilla items.
 */
@UtilityClass
public final class Items {
    /**
     * All recipe parts.
     */
    private static final Map<NamespacedKey, CustomItem> REGISTRY = new ConcurrentHashMap<>();

    /**
     * All recipe parts.
     */
    private static final List<LookupArgParser> ARG_PARSERS = new ArrayList<>();

    /**
     * Register a new custom item.
     *
     * @param key  The key of the item.
     * @param part The item.
     */
    public void registerCustomItem(@NotNull final NamespacedKey key,
                                   @NotNull final CustomItem part) {
        REGISTRY.put(key, part);
    }

    /**
     * Register a new arg parser.
     *
     * @param parser The parser.
     */
    public void registerArgParser(@NotNull final LookupArgParser parser) {
        ARG_PARSERS.add(parser);
    }

    /**
     * Remove an item.
     *
     * @param key The key of the recipe part.
     */
    public void removeCustomItem(@NotNull final NamespacedKey key) {
        REGISTRY.remove(key);
    }

    /**
     * This is the backbone of the entire eco item system.
     * <p>
     * You can lookup a TestableItem for any material, custom item,
     * or item in general, and it will return it with any modifiers
     * passed as parameters. This includes stack size (item amount)
     * and enchantments that should be present on the item.
     * <p>
     * If you want to get an ItemStack instance from this, then just call
     * {@link TestableItem#getItem()}.
     * <p>
     * The advantages of the testable item system are that there is the inbuilt
     * {@link TestableItem#matches(ItemStack)} - this allows to check if any item
     * is that testable item; which may sound negligible but actually it allows for
     * much more power an flexibility. For example, you can have an item with an
     * extra metadata tag, extra lore lines, different display name - and it
     * will still work as long as the test passes. This is very important
     * for custom crafting recipes where other plugins may add metadata
     * values or the play may rename the item.
     *
     * @param key The lookup string.
     * @return The testable item, or an {@link EmptyTestableItem}.
     */
    public TestableItem lookup(@NotNull final String key) {
        if (key.contains("?")) {
            String[] options = key.split("\\?");
            for (String option : options) {
                TestableItem lookup = lookup(option);
                if (!(lookup instanceof EmptyTestableItem)) {
                    return lookup;
                }
            }

            return new EmptyTestableItem();
        }

        String[] args = key.split(" ");
        if (args.length == 0) {
            return new EmptyTestableItem();
        }

        TestableItem item = null;

        int stackAmount = 1;

        String[] split = args[0].toLowerCase().split(":");

        if (split.length == 1) {
            Material material = Material.getMaterial(args[0].toUpperCase());
            if (material == null || material == Material.AIR) {
                return new EmptyTestableItem();
            }
            item = new MaterialTestableItem(material);
        }

        if (split.length == 2) {
            CustomItem part = REGISTRY.get(NamespacedKeyUtils.create(split[0], split[1]));

            /*
            Legacy id:amount format
            This has been superseded by id amount
             */
            if (part == null) {
                Material material = Material.getMaterial(split[0].toUpperCase());
                if (material == null || material == Material.AIR) {
                    return new EmptyTestableItem();
                }
                item = new MaterialTestableItem(material);
                stackAmount = Integer.parseInt(split[1]);
            } else {
                item = part;
            }
        }

        /*
        Legacy namespace:id:amount format
        This has been superseded by namespace:id amount
         */
        if (split.length == 3) {
            CustomItem part = REGISTRY.get(NamespacedKeyUtils.create(split[0], split[1]));
            if (part == null) {
                return new EmptyTestableItem();
            }
            item = part;
            stackAmount = Integer.parseInt(split[2]);
        }

        boolean usingNewStackFormat = false;

        if (args.length >= 2) {
            try {
                stackAmount = Integer.parseInt(args[1]);
                usingNewStackFormat = true;
            } catch (NumberFormatException ignored) {
            }
        }

        if (item == null) {
            return new EmptyTestableItem();
        }

        ItemStack example = item.getItem();
        ItemMeta meta = example.getItemMeta();
        assert meta != null;

        String[] modifierArgs = Arrays.copyOfRange(args, usingNewStackFormat ? 2 : 1, args.length);

        List<Predicate<ItemStack>> predicates = new ArrayList<>();

        for (LookupArgParser argParser : ARG_PARSERS) {
            predicates.add(argParser.parseArguments(modifierArgs, meta));
        }

        example.setItemMeta(meta);
        if (!predicates.isEmpty()) {
            item = new ModifiedTestableItem(
                    item,
                    test -> {
                        for (Predicate<ItemStack> predicate : predicates) {
                            if (!predicate.test(test)) {
                                return false;
                            }
                        }

                        return true;
                    },
                    example
            );
        }

        if (stackAmount == 1) {
            return item;
        } else {
            return new TestableStack(item, stackAmount);
        }
    }

    /**
     * Get if itemStack is a custom item.
     *
     * @param itemStack The itemStack to check.
     * @return If is recipe.
     */
    public boolean isCustomItem(@NotNull final ItemStack itemStack) {
        for (CustomItem item : REGISTRY.values()) {
            if (item.matches(itemStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get custom item from item.
     *
     * @param itemStack The item.
     * @return The custom item, or null if not exists.
     */
    @Nullable
    public CustomItem getCustomItem(@NotNull final ItemStack itemStack) {
        for (CustomItem item : REGISTRY.values()) {
            if (item.matches(itemStack)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Get all registered custom items.
     *
     * @return A set of all items.
     */
    public Set<CustomItem> getCustomItems() {
        return new HashSet<>(REGISTRY.values());
    }
}
