package com.midnightmunchies.technicalservices.ord.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.CatalogNames;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.repository.MenuItemRepository;
import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class SupabaseMenuItemRepository implements MenuItemRepository {
    private static final ParameterizedTypeReference<List<Row>> ROWS = new ParameterizedTypeReference<>() {};
    // Decode reserved values written by earlier adapter versions. New writes
    // use ordinary optional metadata after the issue 7 schema migration.
    private static final String NO_IMAGE_PREFIX = "__midnight_munchies_no_image__/";
    private static final String NO_DESCRIPTION_PREFIX = "__midnight_munchies_no_description__/";
    private final SupabaseCatalogClient catalog;

    public SupabaseMenuItemRepository(SupabaseCatalogClient catalog) { this.catalog = catalog; }

    @Override
    public Optional<MenuItem> findById(UUID id) {
        return catalog.select("Menu_Items", Map.of("id", "eq." + Objects.requireNonNull(id)), ROWS)
                .stream().findFirst().map(Row::item);
    }

    @Override
    public List<MenuItem> findAll() {
        return catalog.select("Menu_Items", Map.of(), ROWS).stream().map(Row::item).toList();
    }

    @Override
    public List<MenuItem> findByRestaurantId(UUID restaurantId) {
        return catalog.select("Menu_Items", Map.of("restaurant_id", "eq." + Objects.requireNonNull(restaurantId)), ROWS)
                .stream().map(Row::item).toList();
    }

    @Override
    public synchronized MenuItem save(MenuItem item) {
        CatalogNames.requireUniqueMenuItem(item, findByRestaurantId(item.getRestaurantId()));
        catalog.save("Menu_Items", new Row(item.getId(), item.getRestaurantId(), item.getName(), item.getPrice(),
                item.getStatus() == MenuItemStatus.AVAILABLE, item.getImage(), item.getDescription()));
        return item;
    }

    @Override
    public boolean deleteById(UUID id) {
        boolean removed = catalog.delete("Menu_Items", Objects.requireNonNull(id).toString());
        if (!removed) throw new CatalogAccessException(new IllegalStateException("Menu deletion returned no rows"));
        return true;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(UUID id, @JsonProperty("restaurant_id") UUID restaurantId,
                      @JsonProperty("item_name") String name, @JsonProperty("item_cost") BigDecimal price,
                      Boolean availability, @JsonProperty("item_image") String image,
                      @JsonProperty("item_description") List<String> description) {
        MenuItem item() {
            String visibleImage = image == null || image.isBlank() || image.equals(NO_IMAGE_PREFIX + id)
                    ? null : image;
            List<String> visibleDescription = description == null || description.equals(List.of(NO_DESCRIPTION_PREFIX + id))
                    ? List.of() : description;
            return new MenuItem(id, restaurantId, name, price,
                    Boolean.TRUE.equals(availability) ? MenuItemStatus.AVAILABLE : MenuItemStatus.SOLD_OUT,
                    visibleImage, visibleDescription);
        }
    }
}
