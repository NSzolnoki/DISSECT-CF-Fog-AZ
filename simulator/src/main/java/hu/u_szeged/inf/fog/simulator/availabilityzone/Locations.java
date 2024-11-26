/**
 * Provides a list of predefined geographical locations and functionality for selecting random locations.
 */
package hu.u_szeged.inf.fog.simulator.availabilityzone;

import java.security.SecureRandom;

public class Locations {
    // Predefined list of cities with their corresponding latitude and longitude
    private static final Object[][] LOCATIONS = {
            { "Budapest", 47.4979, 19.0402 },
            { "Paris", 48.8566, 2.3522 },
            { "London", 51.5074, -0.1278 },
            { "New York", 40.7128, -74.0060 },
            { "Tokyo", 35.6895, 139.6917 },
            { "Berlin", 52.5200, 13.4050 },
            { "Sydney", -33.8688, 151.2093 },
            { "Moscow", 55.7558, 37.6173 },
            { "Rio de Janeiro", -22.9068, -43.1729 },
            { "Cape Town", -33.9249, 18.4241 },
            { "Dubai", 25.276987, 55.296249 },
            { "Rome", 41.9028, 12.4964 },
            { "Madrid", 40.4168, -3.7038 },
            { "Toronto", 43.65107, -79.347015 },
            { "Singapore", 1.3521, 103.8198 },
            { "Hong Kong", 22.3193, 114.1694 },
            { "Los Angeles", 34.0522, -118.2437 },
            { "San Francisco", 37.7749, -122.4194 },
            { "Chicago", 41.8781, -87.6298 },
            { "Beijing", 39.9042, 116.4074 },
            { "Shanghai", 31.2304, 121.4737 },
            { "Bangkok", 13.7563, 100.5018 },
            { "Seoul", 37.5665, 126.9780 },
            { "Mumbai", 19.0760, 72.8777 },
            { "Delhi", 28.7041, 77.1025 },
            { "Istanbul", 41.0082, 28.9784 },
            { "Buenos Aires", -34.6037, -58.3816 },
            { "Mexico City", 19.4326, -99.1332 },
            { "Sao Paulo", -23.5505, -46.6333 },
            { "Jakarta", -6.2088, 106.8456 },
            { "Kuala Lumpur", 3.1390, 101.6869 },
            { "Cairo", 30.0444, 31.2357 },
            { "Johannesburg", -26.2041, 28.0473 },
            { "Lagos", 6.5244, 3.3792 },
            { "Nairobi", -1.286389, 36.817223 },
            { "Athens", 37.9838, 23.7275 },
            { "Helsinki", 60.1699, 24.9384 },
            { "Stockholm", 59.3293, 18.0686 },
            { "Oslo", 59.9139, 10.7522 },
            { "Copenhagen", 55.6761, 12.5683 },
            { "Vienna", 48.2082, 16.3738 },
            { "Warsaw", 52.2297, 21.0122 },
            { "Prague", 50.0755, 14.4378 },
            { "Zurich", 47.3769, 8.5417 },
            { "Amsterdam", 52.3676, 4.9041 },
            { "Lisbon", 38.7169, -9.139 },
            { "Dublin", 53.3498, -6.2603 },
            { "Brussels", 50.8503, 4.3517 }
    };

    /**
     * Selects a random location from the predefined list.
     *
     * @return a {@link Location} object representing a randomly selected location.
     */

    public static Location getRandomLocation() {
        SecureRandom secureRandom = new SecureRandom();
        int index = secureRandom.nextInt(LOCATIONS.length);

        Object[] locationData = LOCATIONS[index];
        return new Location((String) locationData[0], (double) locationData[1], (double) locationData[2]);
    }

    /**
     * Represents a geographical location with a city name, latitude, and longitude.
     */
    public static class Location {
        private final String city;
        private final double latitude;
        private final double longitude;

        /**
         * Constructs a {@code Location} object.
         *
         * @param city      the name of the city
         * @param latitude  the latitude of the location
         * @param longitude the longitude of the location
         */
        public Location(String city, double latitude, double longitude) {
            this.city = city;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        /**
         * @return the name of the city.
         */
        public String getCity() {
            return city;
        }

        /**
         * @return the latitude of the location.
         */
        public double getLatitude() {
            return latitude;
        }

        /**
         * @return the longitude of the location.
         */
        public double getLongitude() {
            return longitude;
        }

        /**
         * Provides a string representation of the location.
         *
         * @return a string representation of the location, including city, latitude,
         *         and longitude.
         */
        @Override
        public String toString() {
            return "Location{" +
                    "city='" + city + '\'' +
                    ", latitude=" + latitude +
                    ", longitude=" + longitude +
                    '}';
        }
    }
}
