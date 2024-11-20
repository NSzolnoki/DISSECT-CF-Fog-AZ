package hu.u_szeged.inf.fog.simulator.availabilityzone;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

/**
 * Represents a region containing multiple availability zones (AZs) with functionality
 * to handle read and write requests using different selection strategies for AZs.
 */
public class Region {
    private List<AvailabilityZone> zones;
    private SelectionStrategy selectionStrategy;

    /**
     * Defines strategies for selecting an Availability Zone (AZ).
     */
    public enum SelectionStrategy {
        NEAREST,       // Select the closest AZ to the user
        LEAST_LOADED,  // Select the AZ with the lowest load
        RANDOM         // Select a random AZ
    }

    /**
     * Constructs a Region with the specified list of availability zones and selection strategy.
     *
     * @param zones    the list of availability zones in the region
     * @param strategy the strategy used to select an AZ for requests
     */
    public Region(List<AvailabilityZone> zones, SelectionStrategy strategy) {
        this.zones = zones;
        this.selectionStrategy = strategy;
    }

    /**
     * Handles a read request by selecting an appropriate AZ based on the strategy,
     * and attempts to read data from the selected AZ.
     *
     * @param request       the user request initiating the read
     * @param data          the data object to be read
     * @param userLatitude  the latitude of the user
     * @param userLongitude the longitude of the user
     */
    public void handleReadRequest(UserRequest request, DataObject data, double userLatitude, double userLongitude) {
        AvailabilityZone selectedZone = getSelectedZone(userLatitude, userLongitude);

        if (selectedZone != null && selectedZone.isAvailable()) {
            System.out.println("Reading data from AZ located in " + selectedZone.getName());
            selectedZone.readData(request, data);
        } else {
            System.out.println("Read request failed: No available AZs.");
        }
    }

    /**
     * Handles a write request by selecting an appropriate AZ based on the strategy,
     * and writes data redundantly across all available AZs.
     *
     * @param request       the user request initiating the write
     * @param data          the data object to be written
     * @param userLatitude  the latitude of the user
     * @param userLongitude the longitude of the user
     */
    public void handleWriteRequest(UserRequest request, DataObject data, double userLatitude, double userLongitude) {
        AvailabilityZone selectedZone = getSelectedZone(userLatitude, userLongitude);

        if (selectedZone != null && selectedZone.isAvailable()) {
            System.out.println("Writing data to the selected AZ first: " + selectedZone.getName());

            long startTime = Timed.getFireCount();
            long completionTime = startTime;

            // Write data to the selected zone
            completionTime = selectedZone.writeData(request, data, completionTime);

            // Propagate data redundantly to other available zones
            for (AvailabilityZone zone : zones) {
                if (zone != selectedZone) {
                    if (zone.isAvailable()) {
                        System.out.println("Propagating data redundantly to AZ at (" + zone.getName() + ")");
                        completionTime = zone.writeData(request, data, completionTime);
                    } else {
                        System.out.println("Skipping unavailable AZ: " + zone.getName());
                    }
                }
            }

            System.out.println("Write request completed: Data stored redundantly across all available AZs.");
        } else {
            System.out.println("Write request failed: No available AZs for initial write.");
        }
    }

    /**
     * Gets the selected AZ based on the configured selection strategy.
     *
     * @param userLatitude  the latitude of the user
     * @param userLongitude the longitude of the user
     * @return the selected AZ, or null if no suitable AZ is found
     */
    private AvailabilityZone getSelectedZone(double userLatitude, double userLongitude) {
        switch (selectionStrategy) {
            case NEAREST:
                return findNearestAZ(userLatitude, userLongitude);
            case LEAST_LOADED:
                return findLeastLoadedAZ();
            case RANDOM:
                return findRandomAZ();
            default:
                return null;
        }
    }

    /**
     * Finds the nearest AZ to the user's location.
     *
     * @param userLatitude  the latitude of the user
     * @param userLongitude the longitude of the user
     * @return the nearest AZ, or null if none are available
     */
    private AvailabilityZone findNearestAZ(double userLatitude, double userLongitude) {
        return zones.stream()
                .filter(AvailabilityZone::isAvailable)
                .min(Comparator.comparingDouble(zone -> zone.calculateDistance(userLatitude, userLongitude)))
                .orElse(null);
    }

    /**
     * Finds the least loaded AZ in the region.
     *
     * @return the least loaded AZ, or null if none are available
     */
    private AvailabilityZone findLeastLoadedAZ() {
        return zones.stream()
                .filter(AvailabilityZone::isAvailable)
                .min(Comparator.comparingDouble(AvailabilityZone::getLoad))
                .orElse(null);
    }

    /**
     * Finds a random AZ from the list of available zones.
     *
     * @return a random AZ, or null if none are available
     */
    private AvailabilityZone findRandomAZ() {
        List<AvailabilityZone> availableZones = zones.stream()
                .filter(AvailabilityZone::isAvailable)
                .collect(Collectors.toList());

        if (availableZones.isEmpty()) return null;

        int index = new Random().nextInt(availableZones.size());
        return availableZones.get(index);
    }
}
