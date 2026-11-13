package com.midnightmunchies.domain.ord;

import java.util.Objects;
import java.util.UUID;

/** A restaurant; menu items and orders refer to its ID rather than owned collections. */
public final class Restaurant {

	private final UUID id;
	private final UUID ownerId;
	private final String name;
	private final RestaurantStatus status;
	private final double latitude;
	private final double longitude;
	private final String logo;
	private final String menu;

	public Restaurant(UUID id, String name, RestaurantStatus status, double latitude, double longitude) {
		this(id, null, name, status, latitude, longitude);
	}

	public Restaurant(UUID id, UUID ownerId, String name, RestaurantStatus status, double latitude, double longitude) {
		this(id, ownerId, name, status, latitude, longitude, null, null);
	}

	public Restaurant(UUID id, UUID ownerId, String name, RestaurantStatus status, double latitude, double longitude,
			String logo, String menu) {
		this.id = Objects.requireNonNull(id, "id must not be null");
		this.ownerId = ownerId;
		this.name = OrdValidation.requireName(CatalogNames.normalize(name));
		this.status = Objects.requireNonNull(status, "status must not be null");
		if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
			throw new IllegalArgumentException("latitude must be finite and between -90 and 90");
		}
		if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("longitude must be finite and between -180 and 180");
		}
		this.latitude = latitude;
		this.longitude = longitude;
		this.logo = logo;
		this.menu = menu;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public String getName() {
		return name;
	}

	public RestaurantStatus getStatus() {
		return status;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public String getLogo() { return logo; }

	public String getMenu() { return menu; }
}
