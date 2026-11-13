package com.midnightmunchies.domain.geo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MontrealServiceArea {

    public static final double MIN_LATITUDE = 45.40;
    public static final double MAX_LATITUDE = 45.71;
    public static final double MIN_LONGITUDE = -73.98;
    public static final double MAX_LONGITUDE = -73.47;

    static final double LATITUDE_SPREAD = 0.0012;
    static final double LONGITUDE_SPREAD = 0.0016;

    static final List<Place> ANCHORS = List.of(
            new Place(45.5017, -73.5693, "Downtown – Place Ville Marie"),
            new Place(45.5048, -73.5772, "Downtown – McGill University"),
            new Place(45.4970, -73.5790, "Downtown – Concordia, Guy St"),
            new Place(45.5046, -73.5571, "Old Montreal – Place d'Armes"),
            new Place(45.5075, -73.5610, "Chinatown – De la Gauchetière St"),
            new Place(45.5085, -73.5664, "Quartier des Spectacles – Place des Arts"),
            new Place(45.5190, -73.5580, "Gay Village – Sainte-Catherine St E"),
            new Place(45.5245, -73.5817, "Plateau Mont-Royal – Mont-Royal Ave E"),
            new Place(45.5240, -73.6000, "Mile End – Saint-Viateur St W"),
            new Place(45.5205, -73.6060, "Outremont – Laurier Ave W"),
            new Place(45.5363, -73.6146, "Little Italy – Jean-Talon Market"),
            new Place(45.5354, -73.6046, "Rosemont – Beaubien St E"),
            new Place(45.5340, -73.6280, "Villeray – Jarry Park"),
            new Place(45.5598, -73.5998, "Saint-Michel – Jarry St E"),
            new Place(45.5590, -73.6560, "Ahuntsic – Fleury St E"),
            new Place(45.5900, -73.6370, "Montréal-Nord – Henri-Bourassa Blvd E"),
            new Place(45.6020, -73.5590, "Anjou – Galeries d'Anjou"),
            new Place(45.5965, -73.5350, "Mercier-Est – Honoré-Beaugrand"),
            new Place(45.5580, -73.5515, "Hochelaga – Olympic Stadium"),
            new Place(45.5495, -73.5450, "Hochelaga-Maisonneuve – Ontario St E"),
            new Place(45.4960, -73.6230, "Côte-des-Neiges – Queen Mary Rd"),
            new Place(45.4860, -73.5880, "Westmount – Greene Ave"),
            new Place(45.4740, -73.6270, "Notre-Dame-de-Grâce – Monkland Village"),
            new Place(45.5140, -73.6830, "Saint-Laurent – Côte-Vertu Blvd"),
            new Place(45.4800, -73.5770, "Saint-Henri – Atwater Market"),
            new Place(45.4935, -73.5640, "Griffintown – Notre-Dame St W"),
            new Place(45.4800, -73.5610, "Pointe-Saint-Charles – Centre St"),
            new Place(45.4613, -73.5672, "Verdun – Wellington St"),
            new Place(45.4462, -73.6034, "LaSalle – Angrignon"),
            new Place(45.4380, -73.6760, "Lachine – Notre-Dame St")
    );

    private MontrealServiceArea() {
    }

    public static boolean contains(double latitude, double longitude) {
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE
                && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    public static Place place(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new IllegalArgumentException("coordinates must be finite numbers");
        }
        if (contains(latitude, longitude)) {
            return new Place(latitude, longitude, null);
        }
        return anchored(mix(Double.doubleToLongBits(latitude) * 31 + Double.doubleToLongBits(longitude)));
    }

    public static Place placeFor(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return anchored(mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17)));
    }

    public static String areaOf(double latitude, double longitude) {
        if (!contains(latitude, longitude)) {
            return null;
        }
        Place nearest = ANCHORS.get(0);
        double best = Double.POSITIVE_INFINITY;
        for (Place anchor : ANCHORS) {
            double distance = Math.hypot(anchor.latitude() - latitude, anchor.longitude() - longitude);
            if (distance < best) {
                best = distance;
                nearest = anchor;
            }
        }
        return nearest.label();
    }

    private static Place anchored(long hash) {
        Place anchor = ANCHORS.get((int) Math.floorMod(hash, (long) ANCHORS.size()));
        long jitter = mix(hash);
        double latitudeOffset = (unit(jitter) * 2 - 1) * LATITUDE_SPREAD;
        double longitudeOffset = (unit(mix(jitter)) * 2 - 1) * LONGITUDE_SPREAD;
        return new Place(anchor.latitude() + latitudeOffset, anchor.longitude() + longitudeOffset, anchor.label());
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public record Place(double latitude, double longitude, String label) {
    }
}
