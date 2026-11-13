package com.midnightmunchies.domain.dash;

import com.midnightmunchies.domain.ord.RestaurantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueBandTests {

    @ParameterizedTest
    @CsvSource({
            "0, GREEN",
            "2, GREEN",
            "3, AMBER",
            "5, AMBER",
            "6, RED",
            "10, RED"
    })
    void openRestaurantBandFollowsQueuedCount(int queued, QueueBand expected) {
        assertEquals(expected, QueueBand.of(RestaurantStatus.OPEN, queued));
    }

    @Test
    void pausedRestaurantIsRedEvenWithEmptyQueue() {
        assertEquals(QueueBand.RED, QueueBand.of(RestaurantStatus.PAUSED, 0));
    }

    @Test
    void closedRestaurantIsRedEvenWithEmptyQueue() {
        assertEquals(QueueBand.RED, QueueBand.of(RestaurantStatus.CLOSED, 0));
    }

    @Test
    void negativeQueuedCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> QueueBand.of(RestaurantStatus.OPEN, -1));
    }

    @Test
    void missingStatusIsRejected() {
        assertThrows(NullPointerException.class, () -> QueueBand.of(null, 0));
    }
}
