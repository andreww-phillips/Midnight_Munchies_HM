package com.midnightmunchies.application.dlv;

import com.midnightmunchies.domain.dlv.DispatchQueue;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DispatchQueueService {
    private final DispatchQueue queue = new DispatchQueue();
    private final Map<UUID, Order> queuedOrders = new LinkedHashMap<>();
    private final Path storageFile;

    @Autowired
    public DispatchQueueService(@Value("${dlv.dispatch-queue-file:./data/dlv-dispatch-queue.txt}") String path) {
        this(Path.of(path));
    }

    DispatchQueueService(Path storageFile) {
        this.storageFile = storageFile.toAbsolutePath().normalize();
        load();
    }

    public synchronized void enqueue(Order order) {
        if (order == null || order.getStatus() != OrderStatus.READY) {
            throw new IllegalArgumentException("Only READY orders can enter the dispatch queue");
        }
        UUID orderId = order.getId();
        if (queuedOrders.containsKey(orderId)) {
            return;
        }

        Order snapshot = snapshot(order);
        List<Order> updated = new ArrayList<>(queuedOrders.values());
        updated.add(snapshot);
        persist(updated);
        queue.enqueue(orderId);
        queuedOrders.put(orderId, snapshot);
    }

    public synchronized Optional<UUID> peek() {
        return queue.peek();
    }

    public synchronized void remove(UUID orderId) {
        if (!queuedOrders.containsKey(orderId)) {
            return;
        }
        List<Order> updated = queuedOrders.values().stream()
                .filter(order -> !order.getId().equals(orderId))
                .toList();
        persist(updated);
        queue.remove(orderId);
        queuedOrders.remove(orderId);
    }

    public synchronized List<UUID> view() {
        return queue.snapshot();
    }

    public synchronized List<Order> restoreOrders() {
        return List.copyOf(queuedOrders.values());
    }

    private void load() {
        if (!Files.exists(storageFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(storageFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                Order order = decode(line);
                if (order.getStatus() != OrderStatus.READY) {
                    throw new IllegalStateException("Persisted dispatch queue contains a non-ready order " + order.getId());
                }
                if (queuedOrders.putIfAbsent(order.getId(), order) == null) {
                    queue.enqueue(order.getId());
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load dispatch queue from " + storageFile, exception);
        }
    }

    private void persist(List<Order> orders) {
        Path parent = storageFile.getParent();
        Path temporaryFile = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = orders.stream().map(this::encode).toList();
            Files.write(temporaryFile, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, storageFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, storageFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist dispatch queue to " + storageFile, exception);
        }
    }

    private String encode(Order order) {
        List<String> fields = new ArrayList<>();
        fields.add(order.getId().toString());
        fields.add(order.getCustomerId().toString());
        fields.add(order.getRestaurantId().toString());
        fields.add(order.getCreatedAt().toString());
        fields.add(Integer.toString(order.getOrderLines().size()));
        for (OrderLine line : order.getOrderLines()) {
            fields.add(line.getMenuItemId().toString());
            fields.add(line.getMenuItemName());
            fields.add(Integer.toString(line.getQuantity()));
            fields.add(line.getUnitPrice().toPlainString());
        }
        return fields.stream().map(this::encodeField).reduce((left, right) -> left + "\t" + right).orElseThrow();
    }

    private Order decode(String encoded) {
        String[] fields = encoded.split("\\t", -1);
        int field = 0;
        UUID id = UUID.fromString(decodeField(fields[field++]));
        UUID customerId = UUID.fromString(decodeField(fields[field++]));
        UUID restaurantId = UUID.fromString(decodeField(fields[field++]));
        java.time.Instant createdAt = java.time.Instant.parse(decodeField(fields[field++]));
        int lineCount = Integer.parseInt(decodeField(fields[field++]));
        if (lineCount < 1 || fields.length != 5 + lineCount * 4) {
            throw new IllegalArgumentException("Persisted dispatch order has an invalid line count");
        }
        List<OrderLine> lines = new ArrayList<>();
        for (int index = 0; index < lineCount; index++) {
            UUID menuItemId = UUID.fromString(decodeField(fields[field++]));
            String name = decodeField(fields[field++]);
            int quantity = Integer.parseInt(decodeField(fields[field++]));
            java.math.BigDecimal price = new java.math.BigDecimal(decodeField(fields[field++]));
            lines.add(name == null ? new OrderLine(menuItemId, quantity, price)
                    : new OrderLine(menuItemId, name, quantity, price));
        }
        return Order.restore(id, customerId, restaurantId, lines, createdAt, OrderStatus.READY, null);
    }

    private String encodeField(String value) {
        if (value == null) {
            return "~";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeField(String value) {
        if (value.equals("~")) {
            return null;
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private Order snapshot(Order order) {
        return Order.restore(order.getId(), order.getCustomerId(), order.getRestaurantId(), order.getOrderLines(),
                order.getCreatedAt(), OrderStatus.READY, null);
    }
}
