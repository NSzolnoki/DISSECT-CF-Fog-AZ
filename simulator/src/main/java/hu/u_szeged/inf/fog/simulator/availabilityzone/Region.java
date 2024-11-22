package hu.u_szeged.inf.fog.simulator.availabilityzone;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

public class Region {
    private List<AvailabilityZone> zones;
    private SelectionStrategy selectionStrategy;
    private List<StorageObject> availableStorageObjects;

    // Map to track the last usage time of each AZ
    private static Map<AvailabilityZone, Long> zoneLastUsage;
    
        public Region(List<AvailabilityZone> zones, SelectionStrategy strategy) {
            this.zones = zones;
            this.selectionStrategy = strategy;
            this.availableStorageObjects = new CopyOnWriteArrayList<>();
            Region.zoneLastUsage = new ConcurrentHashMap<>();
            // Initialize usage timestamps for all zones
            zones.forEach(zone -> zoneLastUsage.put(zone, 0L));
        }
    
        public enum SelectionStrategy {
            NEAREST, // Select the closest AZ to the user
            LEAST_LOADED, // Select the AZ with the lowest load
            RANDOM, // Select a random AZ
            MOST_RECENTLY_USED // Select the most recently used AZ
        }
    
        public List<StorageObject> getAvailableObjects() {
            return availableStorageObjects;
        }
    
        public long handleReadRequest(PhysicalMachineWithLocation userPm, StorageObject data, SelectionStrategy selectionStrategy) {
            long startTime = Timed.getFireCount(); // Capture start time
        
            List<AvailabilityZone> availabilityZones = getSelectedZone(userPm, selectionStrategy, zones);
            if (availabilityZones.size() > 0) {
                
                System.out.println(String.format("User from %s is reading data from AZ located in %s",
                        userPm.getLocation().getCity(), availabilityZones.get(0).getLocation().getCity()));
                return availabilityZones.get(0).readData(userPm, data, availabilityZones.get(0), startTime); // Pass start time
            } else {
                System.out.println("Read request failed: No available AZs.");
                return Timed.getFireCount();
            }
        }
        
    
        private List<AvailabilityZone> getSelectedZone(PhysicalMachineWithLocation pm, SelectionStrategy selectedStrageStrategy, List<AvailabilityZone> selecteableZone) {
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
    
        private List<AvailabilityZone> findNearestAZ(double userLatitude, double userLongitude) {
            // Find all zones sorted by distance, with available zones filtered
            return zones.stream()
                    .filter(AvailabilityZone::isAvailable)
                    .sorted(Comparator.comparingDouble(zone -> zone.calculateDistance(userLatitude, userLongitude)))
                    .collect(Collectors.toList());
        }
        
        private List<AvailabilityZone> findLeastLoadedAZ() {
            // Find all zones sorted by load, with available zones filtered
            return zones.stream()
                    .filter(AvailabilityZone::isAvailable)
                    .sorted(Comparator.comparingDouble(AvailabilityZone::getLoad))
                    .collect(Collectors.toList());
        }
        
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
        
        private List<AvailabilityZone> findMostRecentlyUsedAZ(List<AvailabilityZone> selectableZones) {
            // Find all zones sorted by most recent usage
            return selectableZones.stream()
                    .filter(AvailabilityZone::isAvailable)
                    .sorted((zone1, zone2) -> Long.compare(zoneLastUsage.getOrDefault(zone2, 0L), zoneLastUsage.getOrDefault(zone1, 0L)))
                    .collect(Collectors.toList());
        }
        
    
        public List<AvailabilityZone> getZonesMissingData(String dataId) {
            return zones.stream()
                    .filter(zone -> zone.isAvailable() && zone.getRepository().lookup(dataId) == null)
                    .distinct()
                    .collect(Collectors.toList());
        }
        
    
        public long handleWriteRequest(PhysicalMachineWithLocation userPm, StorageObject data) {
            long startTime = Timed.getFireCount();
            List<AvailabilityZone> availabilityZones = getSelectedZone(userPm, this.selectionStrategy, zones);
        
            if (availabilityZones.size() > 0) {           
    
                return availabilityZones.get(0).writeData(userPm, data, zones); // Delegate write logic to AZ
            } else {
                System.out.println("Write request failed: No available AZs for write.");
                return Timed.getFireCount();
            }
        }
    
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
    
        public static void updateZoneUsage(AvailabilityZone zone) {
            zoneLastUsage.put(zone, Timed.getFireCount());
    }
}
