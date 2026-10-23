package com.midnightmunchies.domain.ord;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared display normalization and catalog name uniqueness rules. */
public final class CatalogNames {
    public static final String RESTAURANT_DUPLICATE = "A restaurant with this name already exists.";
    public static final String MENU_ITEM_DUPLICATE = "This restaurant already has a menu item with this name.";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private CatalogNames() {}

    public static String normalize(String name) {
        return WHITESPACE.matcher(Objects.requireNonNull(name, "name must not be null"))
                .replaceAll(" ").strip();
    }

    private static String key(String name) {
        return normalize(name).toLowerCase(Locale.ROOT);
    }

    /** Call within the repository's atomic save operation, before changing stored data. */
    public static void requireUniqueRestaurant(Restaurant candidate, Collection<Restaurant> restaurants) {
        String name = key(candidate.getName());
        if (restaurants.stream().anyMatch(existing -> !existing.getId().equals(candidate.getId())
                && key(existing.getName()).equals(name))) {
            throw new DuplicateNameException(RESTAURANT_DUPLICATE);
        }
    }

    /** All retained items count, including SOLD_OUT; updates exclude the current item ID. */
    public static void requireUniqueMenuItem(MenuItem candidate, Collection<MenuItem> items) {
        String name = key(candidate.getName());
        if (items.stream().anyMatch(existing -> existing.getRestaurantId().equals(candidate.getRestaurantId())
                && !existing.getId().equals(candidate.getId()) && key(existing.getName()).equals(name))) {
            throw new DuplicateNameException(MENU_ITEM_DUPLICATE);
        }
    }
}
