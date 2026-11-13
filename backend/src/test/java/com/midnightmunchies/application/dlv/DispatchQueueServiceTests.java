package com.midnightmunchies.application.dlv;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchQueueServiceTests {
    @TempDir
    Path tempDir;

    @Test
    void persistsQueueFifoAndReadyOrderSnapshotsAcrossServiceRestart() {
        Path queueFile = tempDir.resolve("dispatch-queue.txt");
        Order first = readyOrder("First Meal");
        Order second = readyOrder("Second Meal");
        DispatchQueueService runningQueue = new DispatchQueueService(queueFile);

        runningQueue.enqueue(first);
        runningQueue.enqueue(second);
        runningQueue.enqueue(first);

        DispatchQueueService restartedQueue = new DispatchQueueService(queueFile);

        assertEquals(List.of(first.getId(), second.getId()), restartedQueue.view());
        assertEquals(OrderStatus.READY, restartedQueue.restoreOrders().getFirst().getStatus());
        assertEquals("First Meal",
                restartedQueue.restoreOrders().getFirst().getOrderLines().getFirst().getMenuItemName());

        restartedQueue.remove(first.getId());
        DispatchQueueService afterDequeue = new DispatchQueueService(queueFile);
        assertEquals(List.of(second.getId()), afterDequeue.view());
        afterDequeue.remove(second.getId());
        assertTrue(new DispatchQueueService(queueFile).view().isEmpty());
    }

    @Test
    void rejectsOrdersBeforeTheyAreReady() {
        DispatchQueueService queue = new DispatchQueueService(tempDir.resolve("dispatch-queue.txt"));
        Order placed = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Meal", 1, new BigDecimal("8.50"))), Instant.now());

        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(placed));
        assertTrue(queue.view().isEmpty());
    }

    private Order readyOrder(String menuItemName) {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), menuItemName, 1, new BigDecimal("8.50"))), Instant.now());
        order.accept();
        order.startPreparing();
        order.markReady();
        order.pullTransitions();
        return order;
    }
}
