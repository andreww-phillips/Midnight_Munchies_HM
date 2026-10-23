package com.midnightmunchies.domain.ord;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

/** A menu entry belonging to exactly one restaurant. */
public final class MenuItem {

	private final UUID id;
	private final UUID restaurantId;
	private final String name;
	private final BigDecimal price;
	private final MenuItemStatus status;
	private final String image;
	private final List<String> description;

	public MenuItem(UUID id, UUID restaurantId, String name, BigDecimal price, MenuItemStatus status) {
		this(id, restaurantId, name, price, status, null, List.of());
	}

	public MenuItem(UUID id, UUID restaurantId, String name, BigDecimal price, MenuItemStatus status,
			String image, List<String> description) {
		this.id = Objects.requireNonNull(id, "id must not be null");
		this.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId must not be null");
		this.name = OrdValidation.requireName(CatalogNames.normalize(name));
		this.price = OrdValidation.requirePrice(price);
		this.status = Objects.requireNonNull(status, "status must not be null");
		this.image = image;
		this.description = description == null ? List.of() : List.copyOf(description);
	}

	public UUID getId() {
		return id;
	}

	public UUID getRestaurantId() {
		return restaurantId;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public MenuItemStatus getStatus() {
		return status;
	}

	public String getImage() { return image; }

	public List<String> getDescription() { return description; }
}
