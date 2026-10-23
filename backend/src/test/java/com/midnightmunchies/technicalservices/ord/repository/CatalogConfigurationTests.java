package com.midnightmunchies.technicalservices.ord.repository;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class CatalogConfigurationTests {
    @Test
    void noSettingsSelectTheInMemorySimulator() {
        CatalogConfiguration configuration = new CatalogConfiguration(new MockEnvironment());
        var restaurants = configuration.restaurantRepository();
        assertInstanceOf(InMemoryRestaurantRepository.class, restaurants);
        assertInstanceOf(InMemoryMenuItemRepository.class, configuration.menuItemRepository(restaurants));
    }

    @Test
    void databaseRepositoriesRequireExplicitSelection() {
        CatalogConfiguration configuration = new CatalogConfiguration(new MockEnvironment()
                .withProperty("catalog.mode", "supabase")
                .withProperty("VITE_SUPABASE_URL", "https://example.supabase.co")
                .withProperty("VITE_SUPABASE_PUBLISHABLE_KEY", "test-key"));
        var restaurants = configuration.restaurantRepository();
        assertInstanceOf(SupabaseRestaurantRepository.class, restaurants);
        assertInstanceOf(SupabaseMenuItemRepository.class, configuration.menuItemRepository(restaurants));
    }

    @Test
    void supabaseSettingsDoNotOverrideTheDefaultMemoryCatalog() {
        CatalogConfiguration configuration = new CatalogConfiguration(new MockEnvironment()
                .withProperty("VITE_SUPABASE_URL", "https://example.supabase.co")
                .withProperty("VITE_SUPABASE_PUBLISHABLE_KEY", "test-key"));
        var restaurants = configuration.restaurantRepository();
        assertInstanceOf(InMemoryRestaurantRepository.class, restaurants);
        assertInstanceOf(InMemoryMenuItemRepository.class, configuration.menuItemRepository(restaurants));
        assertFalse(restaurants.findAll().isEmpty());
    }
}
