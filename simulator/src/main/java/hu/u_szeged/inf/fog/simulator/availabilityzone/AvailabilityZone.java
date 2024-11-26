/**
 * Represents an Availability Zone in a cloud infrastructure.
 * An Availability Zone (AZ) consists of a repository for storing data,
 * a physical machine with a specific location, and other attributes
 * required for simulating cloud operations like data read, write, and redundancy.
 */
package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

public class AvailabilityZone {
    private String name;
    private Repository repository;
    private PhysicalMachineWithLocation pm;
    private Locations.Location location; // Use the Location class here

    /**
     * Constructs an AvailabilityZone instance with the specified attributes.
     *
     * @param name       the name of the availability zone
     * @param repository the repository associated with the zone
     * @param pm         the physical machine with location in the zone
     * @param location   the geographical location of the zone
     */

    public AvailabilityZone(String name, Repository repository, PhysicalMachineWithLocation pm,
            Locations.Location location) {
        this.name = name;
        this.repository = repository;
        this.pm = pm;
        this.location = location;
    }

    /**
     * @return the repository associated with the availability zone.
     */
    public Repository getRepository() {
        return repository;
    }

    /**
     * @return the physical machine with location in the availability zone.
     */
    public PhysicalMachineWithLocation getPm() {
        return pm;
    }

    /**
     * @return the name of the availability zone.
     */
    public String getName() {
        return name;
    }

    /**
     * Calculates the current load of the physical machine in the availability zone.
     *
     * @return the load as a fraction of available free disk space to full space.
     */
    public double getLoad() {
        double pmAvailableDiskSpace = this.pm.localDisk.getFreeStorageCapacity();
        double pmTotalDiskSpace = this.pm.localDisk.getMaxStorageCapacity();
        return pmAvailableDiskSpace / pmTotalDiskSpace;
    }

    /**
     * @return the geographical location of the availability zone.
     */
    public Locations.Location getLocation() {
        return location;
    }

    /**
     * Checks if the physical machine in the availability zone is running.
     *
     * @return {@code true} if the physical machine is running, otherwise
     *         {@code false}.
     */
    public boolean isAvailable() {
        return pm.getState() == PhysicalMachineWithLocation.State.RUNNING;
    }

    /**
     * Calculates the distance between the availability zone and a user-specified
     * location using the Haversine formula.
     *
     * @param userLatitude  the latitude of the user's location
     * @param userLongitude the longitude of the user's location
     * @return the distance in kilometers.
     */
    public double calculateDistance(double userLatitude, double userLongitude) {
        // Haversine formula to calculate distance in km
        double latDiff = Math.toRadians(this.location.getLatitude() - userLatitude);
        double lonDiff = Math.toRadians(this.location.getLongitude() - userLongitude);
        double a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2) +
                Math.cos(Math.toRadians(userLatitude)) * Math.cos(Math.toRadians(this.location.getLatitude())) *
                        Math.sin(lonDiff / 2) * Math.sin(lonDiff / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c; // Earth’s radius in km
    }

    /**
     * Handles the read request for a specified data object, downloading it from the
     * selected availability zone.
     *
     * @param userPm       the user's physical machine initiating the read request
     * @param data         the data object to be read
     * @param selectedZone the availability zone containing the requested data
     * @param startTime    the start time of the operation
     * @return the simulated completion time of the operation.
     */
    public long readData(PhysicalMachineWithLocation userPm, StorageObject data, AvailabilityZone selectedZone,
            long startTime) {
        if (selectedZone.getRepository().lookup(data.id) == null) {
            System.err
                    .println("Data " + data.id + " not found in repository " + selectedZone.getRepository().getName());
            return Timed.getFireCount();
        }

        Repository userRepo = userPm.localDisk;
        if (userRepo.lookup(data.id) != null) {
            System.out.println("Removing existing data " + data.id + " from user repository at: "
                    + userPm.getLocation().getCity());
            userRepo.deregisterObject(data);
        }

        if (userPm.localDisk.getFreeStorageCapacity() < data.size) {
            System.err.println(
                    "Failed to initiate download for data " + data.id + ": Not enough space on the user repo.");
            return Timed.getFireCount();
        }

        System.out.println(
                "Initiating download of " + data.id + " from repository " + selectedZone.getRepository().getName());
        long _startTime = Timed.getFireCount();
        try {
            ResourceConsumption consumption = selectedZone.getRepository().requestContentDelivery(
                    data.id,
                    userRepo,
                    new ResourceConsumption.ConsumptionEvent() {
                        @Override
                        public void conComplete() {
                            long endTime = Timed.getFireCount(); // Capture end time
                            long delta = endTime - _startTime; // Compute delta
                            System.out.println("Download of " + data.id + " completed from repository " +
                                    selectedZone.getLocation().getCity() + " to user repository at " +
                                    userPm.getLocation().getCity() + ". Time taken: " + delta
                                    + " simulated seconds. File size: " + data.size);
                            Region.updateZoneUsage(selectedZone);
                        }

                        @Override
                        public void conCancelled(ResourceConsumption problematic) {
                            System.err.println("Download of " + data.id + " from repository " +
                                    selectedZone.getRepository().getName() + " to user repository at " +
                                    userPm.getLocation().getCity() + " was cancelled.");
                        }
                    });

            if (consumption == null) {
                System.err.println("Failed to initiate download for data " + data.id + ": Network error.");
                return Timed.getFireCount();
            }

            // Return the estimated completion time for the current operation
            return Timed.getFireCount() + consumption.getCompletionDistance();

        } catch (NetworkException e) {
            System.err.println("Failed to download data " + data.id + " from repository " +
                    selectedZone.getRepository().getName() + " due to network issues: " + e.getMessage());
            e.printStackTrace();
            return Timed.getFireCount();
        }
    }

    /**
     * Handles the write request for a data object, ensuring redundancy across
     * multiple availability zones.
     *
     * @param userPm            the user's physical machine initiating the write
     *                          request
     * @param data              the data object to be written
     * @param availabilityZones the list of availability zones for redundant storage
     * @return the simulated completion time of the first write operation.
     */

    public long writeData(PhysicalMachineWithLocation userPm, StorageObject data,
            List<AvailabilityZone> availabilityZones) {

        Repository userRepo = userPm.localDisk;
        long startTime = Timed.getFireCount();

        System.out.println("Handling write request for data: " + data.id + " in AZ: " + this.name);

        // Perform the first write operation
        long firstWriteCompletionTime;

        try {
            ResourceConsumption consumption = userRepo.requestContentDelivery(
                    data.id,
                    this.repository,
                    new ResourceConsumption.ConsumptionEvent() {
                        @Override
                        public void conComplete() {
                            long endTime = Timed.getFireCount();
                            System.out.println("Data successfully written to AZ: " + name +
                                    " at simulated time: " + endTime);
                            Region.updateZoneUsage(availabilityZones.get(0));
                        }

                        @Override
                        public void conCancelled(ResourceConsumption problematic) {
                            System.err.println("Data write to AZ: " + name + " was cancelled.");
                        }
                    });

            if (consumption == null) {
                System.err.println("Write failed for AZ: " + name + ". Not enough space or error.");
                return startTime;
            }

            firstWriteCompletionTime = startTime + consumption.getCompletionDistance();
        } catch (NetworkException e) {
            System.err.println("Network exception occurred during write to AZ: " + name);
            e.printStackTrace();
            return startTime;
        }

        while (Timed.getFireCount() < firstWriteCompletionTime || this.repository.lookup(data.id) == null) {
            Timed.simulateUntilLastEvent();
        }

        System.out.println("Initial write to AZ: " + this.name + " completed. Starting propagation...");

        // Propagate data redundantly between other AZs
        for (AvailabilityZone zone : availabilityZones) {
            if (zone != this && zone.isAvailable() && zone.getRepository().lookup(data.id) == null) {
                try {
                    System.out.println("Initiating propagation to AZ: " + zone.getName());
                    ResourceConsumption consumption = this.repository.requestContentDelivery(
                            data.id,
                            zone.getRepository(),
                            new ResourceConsumption.ConsumptionEvent() {
                                @Override
                                public void conComplete() {
                                    long endTime = Timed.getFireCount();
                                    System.out.println("Data successfully propagated to AZ: " + zone.getName() +
                                            " at simulated time: " + endTime);
                                    Region.updateZoneUsage(zone);
                                }

                                @Override
                                public void conCancelled(ResourceConsumption problematic) {
                                    System.err.println("Data propagation to AZ: " + zone.getName() + " was cancelled.");
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

        long endTime = Timed.getFireCount();
        long delta = endTime - startTime;
        System.out.println("Write request completed: Data with ID: " + data.id +
                " stored redundantly across all available AZs. Time taken: " + delta + " simulated seconds.");
        return firstWriteCompletionTime;
    }

}
