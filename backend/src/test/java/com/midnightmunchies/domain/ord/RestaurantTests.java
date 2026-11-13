package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantTests {

	@ParameterizedTest
	@EnumSource(RestaurantStatus.class)
	void preservesRestaurantDetailsAndStatus(RestaurantStatus status) {
		UUID id = UUID.randomUUID();
		Restaurant restaurant = new Restaurant(id, "Midnight Diner", status, 45.5019, -73.5674);

		assertEquals(id, restaurant.getId());
		assertEquals("Midnight Diner", restaurant.getName());
		assertEquals(status, restaurant.getStatus());
		assertEquals(45.5019, restaurant.getLatitude());
		assertEquals(-73.5674, restaurant.getLongitude());
	}

	@Test
	void acceptsCoordinateBoundaries() {
		assertDoesNotThrow(() -> restaurant(-90, -180));
		assertDoesNotThrow(() -> restaurant(90, 180));
	}

	@ParameterizedTest
	@ValueSource(doubles = {-90.1, 90.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
	void rejectsInvalidLatitude(double latitude) {
		assertThrows(IllegalArgumentException.class, () -> restaurant(latitude, 0));
	}

	@ParameterizedTest
	@ValueSource(doubles = {-180.1, 180.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
	void rejectsInvalidLongitude(double longitude) {
		assertThrows(IllegalArgumentException.class, () -> restaurant(0, longitude));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "\t\n"})
	void rejectsBlankName(String name) {
		assertThrows(IllegalArgumentException.class,
				() -> new Restaurant(UUID.randomUUID(), name, RestaurantStatus.OPEN, 0, 0));
	}

	@Test
	void rejectsMissingRequiredFields() {
		assertThrows(NullPointerException.class, () -> new Restaurant(null, "Diner", RestaurantStatus.OPEN, 0, 0));
		assertThrows(NullPointerException.class, () -> new Restaurant(UUID.randomUUID(), null, RestaurantStatus.OPEN, 0, 0));
		assertThrows(NullPointerException.class, () -> new Restaurant(UUID.randomUUID(), "Diner", null, 0, 0));
	}

	@Test
	void defaultPositionIsStableForTheSameId() {
		UUID id = UUID.fromString("40000000-0000-0000-0000-000000000001");

		assertEquals(Restaurant.defaultPositionFor(id), Restaurant.defaultPositionFor(id));
	}

	@Test
	void defaultPositionsDifferForDifferentIds() {
		Restaurant.Position first = Restaurant.defaultPositionFor(UUID.fromString("40000000-0000-0000-0000-000000000001"));
		Restaurant.Position second = Restaurant.defaultPositionFor(UUID.fromString("40000000-0000-0000-0000-000000000002"));

		assertNotEquals(first, second);
	}

	@Test
	void defaultPositionStaysNearTheCityCenter() {
		for (int i = 0; i < 200; i++) {
			Restaurant.Position position = Restaurant.defaultPositionFor(UUID.randomUUID());
			assertTrue(Math.abs(position.latitude() - Restaurant.CITY_CENTER_LATITUDE) <= 0.016);
			assertTrue(Math.abs(position.longitude() - Restaurant.CITY_CENTER_LONGITUDE) <= 0.016);
			assertTrue(Math.hypot(position.latitude() - Restaurant.CITY_CENTER_LATITUDE,
					position.longitude() - Restaurant.CITY_CENTER_LONGITUDE) >= 0.0079);
		}
	}

	private Restaurant restaurant(double latitude, double longitude) {
		return new Restaurant(UUID.randomUUID(), "Diner", RestaurantStatus.OPEN, latitude, longitude);
	}
}
