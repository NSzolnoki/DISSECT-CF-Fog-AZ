package hu.u_szeged.inf.fog.simulator.availabilityzone;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;

public class Region {
    private List<AvailabilityZone> zones;
    private SelectionStrategy selectionStrategy;
    private List<StorageObject> availableStorageObjects;

    public Region(List<AvailabilityZone> zones, SelectionStrategy strategy) {
        this.zones = zones;
        this.selectionStrategy = strategy;
        this.availableStorageObjects = new CopyOnWriteArrayList<>();
    }

    public enum SelectionStrategy {
        NEAREST, // Select the closest AZ to the user
        LEAST_LOADED, // Select the AZ with the lowest load
        RANDOM // Select a random AZ
    }

    public List<StorageObject> getAvailableObjects(){
        return availableStorageObjects;
    }

    public long handleReadRequest(PhysicalMachineWithLocation userPm, StorageObject data) {
        AvailabilityZone selectedZone = getSelectedZone(userPm.getLocation().getLatitude(), userPm.getLocation().getLongitude());
        // Attempt to read data from the selected zone
        if (selectedZone != null && selectedZone.isAvailable()) {
            System.out.println(String.format("User from %s is reading data from AZ located in %s", userPm.getLocation().getCity(), selectedZone.getLocation().getCity()));
            return selectedZone.readData(userPm, data, selectedZone);
        } else {
            System.out.println("Read request failed: No available AZs.");
            return Timed.getFireCount(); // Return current time on failure
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

        if (availableZones.isEmpty())
            return null;

        int index = new Random().nextInt(availableZones.size());
        return availableZones.get(index);
    }

    public long handleWriteRequest(PhysicalMachineWithLocation userPm, StorageObject data) {
        AvailabilityZone selectedZone = getSelectedZone(userPm.getLocation().getLatitude(), userPm.getLocation().getLongitude());
        Repository userRepo = userPm.localDisk;
        if (selectedZone != null && selectedZone.isAvailable()) {
            System.out.println("Writing data to the selected AZ first: " + selectedZone.getName());

            // Perform the first write operation
            long startTime = Timed.getFireCount();
            long firstWriteCompletionTime;

            try {
                ResourceConsumption consumption = userRepo.requestContentDelivery(
                        data.id,
                        selectedZone.getRepository(),
                        new ResourceConsumption.ConsumptionEvent() {
                            @Override
                            public void conComplete() {
                                long endTime = Timed.getFireCount();
                                System.out.println("Data successfully written to AZ: " + selectedZone.getName() +
                                        " at simulated time: " + endTime);
                            }

                            @Override
                            public void conCancelled(ResourceConsumption problematic) {
                                System.err.println("Data write to AZ: " + selectedZone.getName() + " was cancelled.");
                            }
                        });

                if (consumption == null) {
                    System.err
                            .println("Write failed for AZ: " + selectedZone.getName() + ". Not enough space or error.");
                    return startTime;
                }

                // Calculate the estimated completion time of the first write
                firstWriteCompletionTime = startTime + consumption.getCompletionDistance();
            } catch (NetworkException e) {
                System.err.println("Network exception occurred during write to AZ: " + selectedZone.getName());
                e.printStackTrace();
                return startTime;
            }

            // Wait for the first write to complete and ensure the data is registered in the
            // repository
            while (Timed.getFireCount() < firstWriteCompletionTime
                    || selectedZone.getRepository().lookup(data.id) == null) {
                Timed.simulateUntilLastEvent();
            }

            System.out
                    .println("Initial write to AZ: " + selectedZone.getName() + " completed. Starting propagation...");

            // Propagate data redundantly between other AZs
            for (AvailabilityZone zone : zones) {
                if (zone != selectedZone && zone.isAvailable()) {
                    try {
                        System.out.println("Initiating propagation to AZ: " + zone.getName());
                        ResourceConsumption consumption = selectedZone.getRepository().requestContentDelivery(
                                data.id,
                                zone.getRepository(),
                                new ResourceConsumption.ConsumptionEvent() {
                                    @Override
                                    public void conComplete() {
                                        long endTime = Timed.getFireCount();
                                        System.out.println("Data with ID: " + data.id + " successfully propagated to AZ: " + zone.getName() +
                                                " at simulated time: " + endTime);
                                    }

                                    @Override
                                    public void conCancelled(ResourceConsumption problematic) {
                                        System.err.println(
                                                "Data propagation to AZ: " + zone.getName() + " was cancelled.");
                                    }
                                });

                        if (consumption == null) {
                            System.err.println("Failed to initiate propagation to AZ: " + zone.getName());
                        }
                    } catch (NetworkException e) {
                        System.err.println("Network exception during propagation to AZ: " + zone.getName());
                        e.printStackTrace();
                    }
                } else if (!zone.isAvailable()) {
                    System.out.println("Skipping unavailable AZ: " + zone.getName());
                }
            }

            System.out.println("Write request completed: Data with ID: " + data.id + " stored redundantly across all available AZs.");
            return firstWriteCompletionTime;
        } else {
            System.out.println("Write request failed: No available AZs for initial write.");
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

}
