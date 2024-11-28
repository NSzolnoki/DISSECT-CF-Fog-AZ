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
import hu.u_szeged.inf.fog.simulator.availabilityzone.SelectionStrategyEnum.SelectionStrategy;

public class Region {
    private List<AvailabilityZone> zones;
    private SelectionStrategy selectionStrategy;
    private List<StorageObject> availableStorageObjects;
    public StatisticsCollector statisticsCollector = new StatisticsCollector();

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
            AvailabilityZone selectedZone = availabilityZones.get(0);

            System.out.println(String.format("User from %s is reading data from AZ located in %s",
                    userPm.getLocation().getCity(), selectedZone.getLocation().getCity()));

            long readCompletionTime = selectedZone.readData(userPm, data, selectedZone, startTime, statisticsCollector);

            return readCompletionTime; // Pass back the completion time
        } else {
            // Log the failed read operation in statistics
            statisticsCollector.logReadFailure(userPm.getLocation().getCity(), "NO AVAILABLE ZONE", data.id);

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
            case LOWEST_LATENCY:
                return findZonesByLatency(pm.localDisk.getName());
            default:
                return null;
        }
    }

    /**
     * Finds and returns a list of availability zones sorted by their latency to a
     * specified target repository.
     * Only zones that are currently available are included in the result.
     *
     * <p>
     * This method retrieves the latency from each zone's repository to the
     * specified target repository
     * and sorts the zones in ascending order of latency. If a latency value is
     * missing for a zone,
     * it defaults to {@code Integer.MAX_VALUE}, placing such zones at the end of
     * the list.
     * </p>
     *
     * @param targetRepoName the name of the target repository for which latency is
     *                       calculated
     * @return a list of {@code AvailabilityZone} objects sorted by latency to the
     *         specified target repository,
     *         including only zones that are currently available
     */
    private List<AvailabilityZone> findZonesByLatency(String targetRepoName) {
        // Sort zones by latency to the target repository and filter available zones
        return zones.stream()
                .filter(AvailabilityZone::isAvailable) // Only include available zones
                .sorted(Comparator.comparingInt(zone -> {
                    // Get the latency for each zone's repository to the target repository
                    Map<String, Integer> latencies = zone.getPm().localDisk.getLatencies();
                    return latencies.getOrDefault(targetRepoName, Integer.MAX_VALUE); // Default to a high value if
                                                                                      // latency is missing
                }))
                .collect(Collectors.toList());
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
     * Handles a write request for a data object, ensuring redundancy across
     * availability zones.
     *
     * @param userPm the physical machine making the write request
     * @param data   the data object to write
     * @return the simulated completion time of the write operation.
     */
    public long handleWriteRequest(PhysicalMachineWithLocation userPm, StorageObject data) {
        // Step 1: Select the AZs using the selection strategy
        List<AvailabilityZone> availabilityZones = getSelectedZone(userPm, this.selectionStrategy, zones);

        if (availabilityZones.size() > 0) {
            // Step 2: Get the selected AZ for the initial write
            AvailabilityZone selectedZone = availabilityZones.get(0);

            // Step 4: Perform the write operation
            long completionTime = selectedZone.writeData(userPm, data, zones, statisticsCollector);

            // Return the completion time for further processing
            return completionTime;
        } else {
            // Step 7: Log a failure case for write request
            statisticsCollector.logWriteFailure(userPm.getLocation().getCity(), "NO AVAILABLE ZONE", data.id);
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
