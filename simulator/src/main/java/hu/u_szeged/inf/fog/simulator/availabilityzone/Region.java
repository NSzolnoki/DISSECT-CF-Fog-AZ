package hu.u_szeged.inf.fog.simulator.availabilityzone;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

public class Region {
    private List<AvailabilityZone> zones;
    private SelectionStrategy selectionStrategy;

    public Region(List<AvailabilityZone> zones, SelectionStrategy strategy) {
        this.zones = zones;
        this.selectionStrategy = strategy;
    }

    public enum SelectionStrategy {
        NEAREST,       // Select the closest AZ to the user
        LEAST_LOADED,  // Select the AZ with the lowest load
        RANDOM         // Select a random AZ
    }

    public void handleReadRequest(UserRequest request, DataObject data, double userLatitude, double userLongitude) {
        AvailabilityZone selectedZone = getSelectedZone(userLatitude, userLongitude);

        // Attempt to read data from the selected zone
        if (selectedZone != null && selectedZone.isAvailable()) {
            System.out.println("Reading data from AZ located in " + selectedZone.getName());
            selectedZone.readData(request, data);
        } else {
            System.out.println("Read request failed: No available AZs.");
        }
    }

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

    private AvailabilityZone findNearestAZ(double userLatitude, double userLongitude) {
        return zones.stream()
                .filter(AvailabilityZone::isAvailable) // Only consider available AZs
                .min(Comparator.comparingDouble(zone -> zone.calculateDistance(userLatitude, userLongitude)))
                .orElse(null);
    }

    private AvailabilityZone findLeastLoadedAZ() {
        return zones.stream()
                .filter(AvailabilityZone::isAvailable) // Only consider available AZs
                .min(Comparator.comparingDouble(AvailabilityZone::getLoad))
                .orElse(null);
    }

    private AvailabilityZone findRandomAZ() {
        List<AvailabilityZone> availableZones = zones.stream()
                .filter(AvailabilityZone::isAvailable)
                .collect(Collectors.toList());

        if (availableZones.isEmpty()) return null;

        int index = new Random().nextInt(availableZones.size());
        return availableZones.get(index);
    }

    public void handleWriteRequest(UserRequest request, DataObject data, double userLatitude, double userLongitude) {
        AvailabilityZone selectedZone = getSelectedZone(userLatitude, userLongitude);
    
        if (selectedZone != null && selectedZone.isAvailable()) {
            System.out.println("Writing data to the selected AZ first: " + selectedZone.getName());
    
            // Track the start time of each write event sequentially
            long startTime = Timed.getFireCount();
            long completionTime = startTime; // Track completion time of each sequential write
    
            // Write data to the selected zone
            completionTime = selectedZone.writeData(request, data, completionTime);
    
            // Propagate data redundantly to the other available zones in sequence
            for (AvailabilityZone zone : zones) {
                if (zone != selectedZone) {
                    // Check availability again before each write attempt
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
    
}
