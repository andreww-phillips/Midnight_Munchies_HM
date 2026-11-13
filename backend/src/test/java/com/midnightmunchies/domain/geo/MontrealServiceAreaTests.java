package com.midnightmunchies.domain.geo;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MontrealServiceAreaTests {

    @Test
    void coordinatesInsideMontrealAreKeptExactly() {
        MontrealServiceArea.Place place = MontrealServiceArea.place(45.5048, -73.5772);

        assertEquals(45.5048, place.latitude());
        assertEquals(-73.5772, place.longitude());
        assertNull(place.label());
    }

    @Test
    void coordinatesOutsideMontrealAreMovedNextToAnAnchor() {
        double[][] outside = {{0, 0}, {48.8566, 2.3522}, {-33.86, 151.2}, {90, 180}, {45.39, -73.5}, {45.5, -74.5}};

        for (double[] coordinates : outside) {
            MontrealServiceArea.Place place = MontrealServiceArea.place(coordinates[0], coordinates[1]);
            assertTrue(MontrealServiceArea.contains(place.latitude(), place.longitude()));
            assertNearAnAnchor(place);
        }
    }

    @Test
    void theSameInputAlwaysGivesTheSamePlace() {
        assertEquals(MontrealServiceArea.place(10, 20), MontrealServiceArea.place(10, 20));
        UUID id = UUID.fromString("40000000-0000-0000-0000-000000000001");
        assertEquals(MontrealServiceArea.placeFor(id), MontrealServiceArea.placeFor(id));
    }

    @Test
    void differentInputsSpreadAcrossTheCity() {
        Set<String> labels = new HashSet<>();
        Set<MontrealServiceArea.Place> places = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            MontrealServiceArea.Place place = MontrealServiceArea.placeFor(UUID.randomUUID());
            labels.add(place.label());
            places.add(place);
            assertNearAnAnchor(place);
        }

        assertTrue(labels.size() >= 20);
        assertEquals(300, places.size());
    }

    @Test
    void outsideCoordinatesSpreadAcrossAnchorsToo() {
        Random random = new Random(7);
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            labels.add(MontrealServiceArea.place(random.nextDouble() * 180 - 90, random.nextDouble() * 360 - 180).label());
        }

        assertTrue(labels.size() >= 20);
    }

    @Test
    void everyAnchorIsInsideTheServiceArea() {
        for (MontrealServiceArea.Place anchor : MontrealServiceArea.ANCHORS) {
            assertTrue(MontrealServiceArea.contains(anchor.latitude(), anchor.longitude()), anchor.label());
            assertNotNull(anchor.label());
        }
    }

    @Test
    void areaOfNamesTheNearestNeighbourhoodInsideMontreal() {
        assertEquals("Downtown – McGill University", MontrealServiceArea.areaOf(45.5048, -73.5772));
        for (int i = 0; i < 100; i++) {
            MontrealServiceArea.Place place = MontrealServiceArea.placeFor(UUID.randomUUID());
            assertNotNull(MontrealServiceArea.areaOf(place.latitude(), place.longitude()));
        }
        assertNull(MontrealServiceArea.areaOf(0, 0));
    }

    @Test
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MontrealServiceArea.place(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> MontrealServiceArea.place(0, Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> MontrealServiceArea.placeFor(null));
    }

    private static void assertNearAnAnchor(MontrealServiceArea.Place place) {
        MontrealServiceArea.Place anchor = MontrealServiceArea.ANCHORS.stream()
                .filter(candidate -> candidate.label().equals(place.label()))
                .findFirst()
                .orElseThrow();
        assertTrue(Math.abs(place.latitude() - anchor.latitude()) <= MontrealServiceArea.LATITUDE_SPREAD);
        assertTrue(Math.abs(place.longitude() - anchor.longitude()) <= MontrealServiceArea.LONGITUDE_SPREAD);
    }
}
