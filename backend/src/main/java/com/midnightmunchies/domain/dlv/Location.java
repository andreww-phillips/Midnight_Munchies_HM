package com.midnightmunchies.domain.dlv;

/** A courier's geographic position, independent of routing or travel simulation. */
public record Location(double latitude, double longitude) {

	public Location {
		if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
			throw new IllegalArgumentException("latitude must be finite and between -90 and 90");
		}
		if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("longitude must be finite and between -180 and 180");
		}
	}
}
