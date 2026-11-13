package com.midnightmunchies.domain.dlv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LocationTests {

	@Test
	void acceptsCoordinatesIncludingBoundaries() {
		Location location = new Location(45.497, -73.579);
		assertEquals(45.497, location.latitude());
		assertEquals(-73.579, location.longitude());
		assertDoesNotThrow(() -> new Location(-90, -180));
		assertDoesNotThrow(() -> new Location(90, 180));
	}

	@ParameterizedTest
	@ValueSource(doubles = {-90.001, 90.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
	void rejectsInvalidLatitude(double latitude) {
		assertThrows(IllegalArgumentException.class, () -> new Location(latitude, 0));
	}

	@ParameterizedTest
	@ValueSource(doubles = {-180.001, 180.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
	void rejectsInvalidLongitude(double longitude) {
		assertThrows(IllegalArgumentException.class, () -> new Location(0, longitude));
	}
}
