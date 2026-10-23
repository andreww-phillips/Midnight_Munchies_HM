package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.repository.MenuItemRepository;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import com.midnightmunchies.technicalservices.ord.DemoCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class CatalogConfiguration {
    private final SupabaseCatalogClient catalog;

    public CatalogConfiguration(Environment environment) {
        String mode = environment.getProperty("catalog.mode", "memory");
        if (!mode.equals("memory") && !mode.equals("supabase")) {
            throw new IllegalArgumentException("catalog.mode must be memory or supabase");
        }
        String url = environment.getProperty("SUPABASE_URL", environment.getProperty("VITE_SUPABASE_URL", ""));
        String key = environment.getProperty("SUPABASE_KEY", environment.getProperty("VITE_SUPABASE_PUBLISHABLE_KEY", ""));
        if (mode.equals("memory")) {
            catalog = null;
        } else {
            if (url.isBlank() || key.isBlank()) {
                throw new IllegalArgumentException("Supabase URL and key are required. Configure frontend/.env or SUPABASE_URL/SUPABASE_KEY for the backend.");
            }
            catalog = new SupabaseCatalogClient(url, key);
        }
    }

    @Bean
    public RestaurantRepository restaurantRepository() {
        return catalog == null ? new InMemoryRestaurantRepository() : new SupabaseRestaurantRepository(catalog);
    }

    @Bean
    public MenuItemRepository menuItemRepository(RestaurantRepository restaurants) {
        if (catalog != null) return new SupabaseMenuItemRepository(catalog);
        MenuItemRepository items = new InMemoryMenuItemRepository();
        // Demo data belongs only in the local simulator, never in the shared database.
        new DemoCatalog(restaurants, items);
        return items;
    }
}
