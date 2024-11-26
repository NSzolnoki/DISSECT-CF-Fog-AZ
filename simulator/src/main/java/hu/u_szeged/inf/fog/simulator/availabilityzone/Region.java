/**
 * Represents a cloud region containing multiple Availability Zones (AZs).
 * Provides mechanisms for handling data read and write requests, ensuring redundancy,
 * and selecting zones based on various strategies.
 */
package hu.u_szeged.inf.fog.simulator.availabilityzone;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

public class Region {
    private List<AvailabilityZone> zones;
    private SelectionStrategy selectionStrategy;
    private List<StorageObject> availableStorageObjects;

    // Map to track the last usage time of each AZ
    private static Map<AvailabilityZone, Long> zoneLastUsage;

    /**
     * Constructs a Region instance with the specified Availability Zones and
     * selection strategy.
     *
     * @param zones    the list of availability zones in the region
     * @param strategy the strategy for selecting availability zones
     */
    public Region(List<AvailabilityZone> zones, SelectionStrategy strategy) {
        this.zones = zones;
        this.selectionStrategy = strategy;
        this.availableStorageObjects = new CopyOnWriteArrayList<>();
        Region.zoneLastUsage = new ConcurrentHashMap<>();
        // Initialize usage timestamps for all zones
        zones.forEach(zone -> zoneLastUsage.put(zone, 0L));
    }

    /**
     * Represents the strategy for selecting an availability zone.
     */
    public enum SelectionStrategy {
        NEAREST, // Select the closest AZ to the user
        LEAST_LOADED, // Select the AZ with the lowest load
        RANDOM, // Select a random AZ
        MOST_RECENTLY_USED // Select the most recently used AZ
    }

    /**
     * @return the list of storage objects available in all zones.
     */
    public List<StorageObject> getAvailableObjects() {
        return availableStorageObjects;
    }

    /**
     * Handles a read request for a data object using the specified selection
     * strategy.
     *
     * @param userPm            the physical machine making the read request
     * @param data              the data object to read
     * @param selectionStrategy the strategy for selecting the availability zone
     * @return the simulated completion time of the read operation.
     */
    public long handleReadRequest(PhysicalMachineWithLocation userPm, StorageObject data,
            SelectionStrategy selectionStrategy) {
        long startTime = Timed.getFireCount(); // Capture start time

        List<AvailabilityZone> availabilityZones = getSelectedZone(userPm, selectionStrategy, zones);
        if (availabilityZones.size() > 0) {

            System.out.println(String.format("User from %s is reading data from AZ located in %s",
                    userPm.getLocation().getCity(), availabilityZones.get(0).getLocation().getCity()));
            return availabilityZones.get(0).readData(userPm, data, availabilityZones.get(0), startTime); // Pass start
                                                                                                         // time
        } else {
            System.out.println("Read request failed: No available AZs.");
            return Timed.getFireCount();
        }
    }

    /**
     * Gets a list of availability zones based on the specified selection strategy.
     *
     * @param pm                     the physical machine making the request
     * @param selectedStrageStrategy the selection strategy to apply
     * @param selectableZone         the list of availability zones to consider
     * @return a list of selected availability zones based on the strategy.
     */
    private List<AvailabilityZone> getSelectedZone(PhysicalMachineWithLocation pm,
            SelectionStrategy selectedStrageStrategy, List<AvailabilityZone> selecteableZone) {
        switch (selectedStrageStrategy) {
            case NEAREST:
                return findNearestAZ(pm.getLocation().getLatitude(), pm.getLocation().getLongitude());
            case LEAST_LOADED:
                return findLeastLoadedAZ();
            case RANDOM:
                return findRandomAZ();
            case MOST_RECENTLY_USED:
                return findMostRecentlyUsedAZ(selecteableZone);
            default:
                return null;
        }
    }

    /**
     * Finds the nearest availability zones based on the user's location.
     *
     * @param userLatitude  the user's latitude
     * @param userLongitude the user's longitude
     * @return a list of the nearest availability zones.
     */
    private List<AvailabilityZone> findNearestAZ(double userLatitude, double userLongitude) {
        // Find all zones sorted by distance, with available zones filtered
        return zones.stream()
                .filter(AvailabilityZone::isAvailable)
                .sorted(Comparator.comparingDouble(zone -> zone.calculateDistance(userLatitude, userLongitude)))
                .collect(Collectors.toList());
    }

    /**
     * Finds the least loaded availability zones.
     *
     * @return a list of the least loaded availability zones.
     */
    private List<AvailabilityZone> findLeastLoadedAZ() {
        // Find all zones sorted by load, with available zones filtered
        return zones.stream()
                .filter(AvailabilityZone::isAvailable)
                .sorted(Comparator.comparingDouble(AvailabilityZone::getLoad))
                .collect(Collectors.toList());
    }

    /**
     * Finds a random availability zone.
     *
     * @return a list of availability zones in random order.
     */
    private List<AvailabilityZone> findRandomAZ() {
        List<AvailabilityZone> availableZones = zones.stream()
                .filter(AvailabilityZone::isAvailable)
                .collect(Collectors.toList());
        if (availableZones.isEmpty())
            return Collections.emptyList();
        // Shuffle the list to return zones in random order
        Collections.shuffle(availableZones, new Random());
        return availableZones;
    }

    /**
     * Finds the most recently used availability zones.
     *
     * @param selectableZones the list of selectable zones
     * @return a list of the most recently used availability zones.
     */
    private List<AvailabilityZone> findMostRecentlyUsedAZ(List<AvailabilityZone> selectableZones) {
        // Find all zones sorted by most recent usage
        return selectableZones.stream()
                .filter(AvailabilityZone::isAvailable)
                .sorted((zone1, zone2) -> Long.compare(zoneLastUsage.getOrDefault(zone2, 0L),
                        zoneLastUsage.getOrDefault(zone1, 0L)))
                .collect(Collectors.toList());
    }

    /**
     * Finds availability zones missing a specific data object.
     *
     * @param dataId the ID of the data object to check
     * @return a list of availability zones missing the data object.
     */
    public List<AvailabilityZone> getZonesMissingData(String dataId) {
        return zones.stream()
                .filter(zone -> zone.isAvailable() && zone.getRepository().lookup(dataId) == null)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Handles a write request for a data object, ensuring redundancy across
     * availability zones.
     *
     * @param userPm the physical machine making the write request
     * @param data   the data object to write
     * @return the simulated completion time of the write operation.
     */
    public long handleWriteRequest(PhysicalMachineWithLocation userPm, StorageObject data) {
        List<AvailabilityZone> availabilityZones = getSelectedZone(userPm, this.selectionStrategy, zones);

        if (availabilityZones.size() > 0) {

            return availabilityZones.get(0).writeData(userPm, data, zones); // Delegate write logic to AZ
        } else {
            System.out.println("Write request failed: No available AZs for write.");
            return Timed.getFireCount();
        }
    }

    /**
     * Checks if a data object is available in all availability zones.
     *
     * @param storageObject the data object to check
     * @return {@code true} if the data object is available in all zones,
     *         {@code false} otherwise.
     */
    public boolean isDataAvailableInAllAZs(StorageObject storageObject) {
        for (AvailabilityZone zone : zones) {
            if (zone.getRepository().lookup(storageObject.id) == null) {
                return false;
            }
        }
        System.out.println("Data is available in all AZs.");
        availableStorageObjects.add(storageObject);
        return true;
    }

    /**
     * Updates the usage timestamp of the specified availability zone.
     *
     * @param zone the availability zone whose usage timestamp is to be updated
     */
    public static void updateZoneUsage(AvailabilityZone zone) {
        zoneLastUsage.put(zone, Timed.getFireCount());
    }
}
