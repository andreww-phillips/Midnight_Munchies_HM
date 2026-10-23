package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.CatalogNames;
import com.midnightmunchies.domain.ord.DuplicateNameException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SupabaseCatalogClient {
    private static final int PAGE_SIZE = 1000;
    private static final Logger LOG = LoggerFactory.getLogger(SupabaseCatalogClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final RestClient client;

    public SupabaseCatalogClient(String url, String key) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestClient.Builder builder = RestClient.builder().baseUrl(url.replaceAll("/+$", "") + "/rest/v1")
                .requestFactory(factory).defaultHeader("apikey", key);
        // Legacy anon/service_role keys are JWTs; newer publishable/secret keys
        // belong only in apikey, because they are not valid bearer tokens.
        if (key.startsWith("eyJ") && key.split("\\.").length == 3) {
            builder.defaultHeader("Authorization", "Bearer " + key);
        }
        client = builder.build();
    }

    public <T> List<T> select(String table, Map<String, String> filters,
                              ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> result = new ArrayList<>();
            for (int offset = 0; ; offset += PAGE_SIZE) {
                int pageOffset = offset;
                List<T> page = client.get().uri(builder -> {
                    builder.path("/" + table).queryParam("select", "*").queryParam("order", "id")
                            .queryParam("limit", PAGE_SIZE).queryParam("offset", pageOffset);
                    filters.forEach(builder::queryParam);
                    return builder.build();
                }).retrieve().body(type);
                if (page == null) throw new IllegalStateException("Missing catalog response");
                result.addAll(page);
                if (page.size() < PAGE_SIZE) return List.copyOf(result);
            }
        } catch (RestClientException exception) {
            throw failure("read", table, exception);
        } catch (IllegalStateException exception) {
            throw new CatalogAccessException(exception);
        }
    }

    public void save(String table, Object row) {
        try {
            client.post().uri("/" + table).header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .body(row).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw failure("save", table, exception);
        }
    }

    public boolean delete(String table, String id) {
        try {
            List<Map<String, Object>> removed = client.delete()
                    .uri(builder -> builder.path("/" + table).queryParam("id", "eq." + id).build())
                    .header("Prefer", "return=representation")
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            return removed != null && !removed.isEmpty();
        } catch (RestClientException exception) {
            throw failure("delete", table, exception);
        }
    }

    private RuntimeException failure(String operation, String table, RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            String code = "";
            String message = "";
            try {
                var error = JSON.readTree(response.getResponseBodyAsString());
                code = error.path("code").asText("");
                message = error.path("message").asText("");
            } catch (RuntimeException ignored) {
                // A proxy may return HTML instead of a PostgREST error object.
            }
            // Log diagnostic codes, never request headers, keys, or row details.
            LOG.warn("Supabase {} {} failed: HTTP {}, database code {}, message {}",
                    operation, table, response.getStatusCode().value(), code, message);
            if (code.equals("23505")) {
                if (table.equals("Restaurant") && message.contains("restaurants_normalized_name_unique")) {
                    return new DuplicateNameException(CatalogNames.RESTAURANT_DUPLICATE);
                }
                if (table.equals("Menu_Items") && message.contains("menu_items_restaurant_name_unique")) {
                    return new DuplicateNameException(CatalogNames.MENU_ITEM_DUPLICATE);
                }
            }
            if (table.equals("Menu_Items") && (code.equals("23505") || code.equals("23502"))) {
                return new CatalogAccessException(
                        "The menu database needs the latest catalog schema updates. Item names must be unique within each restaurant, names may repeat across restaurants, and images and descriptions must be optional.",
                        409, exception);
            }
        } else {
            LOG.warn("Supabase {} {} failed: {}", operation, table, exception.getClass().getSimpleName());
        }
        return new CatalogAccessException(exception);
    }
}
