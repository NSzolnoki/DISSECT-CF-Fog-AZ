package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;

/**
 * Represents an Availability Zone (AZ) in a cloud simulation.
 * Each AZ contains a repository, a physical machine, and geographical coordinates.
 */
public class AvailabilityZone {
    private Repository repository;
    private PhysicalMachine pm;
    private double latitude;
    private double longitude;
    private String name;

    /**
     * Constructs an AvailabilityZone with the given properties.
     *
     * @param name       the name of the availability zone
     * @param repository the repository associated with the availability zone
     * @param pm         the physical machine in the availability zone
     * @param latitude   the latitude of the availability zone
     * @param longitude  the longitude of the availability zone
     */
    public AvailabilityZone(String name, Repository repository, PhysicalMachine pm, double latitude, double longitude) {
        this.name = name;
        this.repository = repository;
        this.pm = pm;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Gets the physical machine associated with this availability zone.
     *
     * @return the physical machine
     */
    public PhysicalMachine getPm() {
        return pm;
    }

    /**
     * Gets the name of this availability zone.
     *
     * @return the name of the availability zone
     */
    public String getName() {
        return name;
    }

    /**
     * Calculates the load of the physical machine.
     *
     * @return the load of the physical machine as a fraction of available CPU to total CPU
     */
    public double getLoad() {
        double pmAvailableCpu = this.pm.availableCapacities.getRequiredCPUs();
        double pmTotalCpu = this.pm.getCapacities().getRequiredCPUs();
        return pmAvailableCpu / pmTotalCpu;
    }

    /**
     * Gets the latitude of this availability zone.
     *
     * @return the latitude
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Gets the longitude of this availability zone.
     *
     * @return the longitude
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * Checks if the availability zone is currently available.
     *
     * @return true if the physical machine is running, false otherwise
     */
    public boolean isAvailable() {
        return pm.getState() == PhysicalMachine.State.RUNNING;
    }

    /**
     * Calculates the distance between the availability zone and a user's location using the Haversine formula.
     * https://www.geeksforgeeks.org/haversine-formula-to-find-distance-between-two-points-on-a-sphere/
     *
     * @param userLatitude  the user's latitude
     * @param userLongitude the user's longitude
     * @return the distance in kilometers
     */
    public double calculateDistance(double userLatitude, double userLongitude) {
        double latDiff = Math.toRadians(latitude - userLatitude);
        double lonDiff = Math.toRadians(longitude - userLongitude);
        double a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2) +
                Math.cos(Math.toRadians(userLatitude)) * Math.cos(Math.toRadians(latitude)) *
                        Math.sin(lonDiff / 2) * Math.sin(lonDiff / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c; // Earth's radius in kilometers
    }

    /**
     * Reads data from the repository associated with this availability zone.
     *
     * @param request the user request initiating the read
     * @param data    the data object to be read
     */
    public void readData(UserRequest request, DataObject data) {
        if (repository.lookup(data.getName()) == null) {
            System.out.println("Data " + data.getName() + " not found in repository " + repository.getName());
            return;
        }

        System.out.println("Initiating read of " + data.getName() + " from repository " + repository.getName());

        try {
            repository.fetchObjectToMemory(data.toStorageObject(), new ResourceConsumption.ConsumptionEvent() {
                @Override
                public void conComplete() {
                    long endTime = Timed.getFireCount();
                    System.out.println(
                            "Completed read of " + data.getName() + " from repository " + repository.getName() +
                                    " at simulated time: " + endTime);
                }

                @Override
                public void conCancelled(ResourceConsumption problematic) {
                    System.out.println("Read of " + data.getName() + " from repository " + repository.getName()
                            + " was cancelled.");
                }
            });
        } catch (NetworkException e) {
            System.err.println("Failed to read data " + data.getName() + " due to network issues.");
        }
    }

    /**
     * Writes data to the repository associated with this availability zone.
     *
     * @param request               the user request initiating the write
     * @param data                  the data object to be written
     * @param previousCompletionTime the time when the previous write completed
     * @return the expected completion time of this write
     */
    public long writeData(UserRequest request, DataObject data, long previousCompletionTime) {
        while (Timed.getFireCount() < previousCompletionTime) {
            Timed.simulateUntilLastEvent();
        }

        System.out.println("Initiating write of " + data.getName() + " to repository " + repository.getName());

        boolean success = repository.registerObject(data.toStorageObject());
        if (!success) {
            System.err.println("Failed to write data " + data.getName() + ": not enough space in repository.");
            return previousCompletionTime;
        }

        long startTime = Timed.getFireCount();

        new DeferredEvent(calculateTransferTime(data)) {
            @Override
            protected void eventAction() {
                long endTime = Timed.getFireCount();
                System.out.println("Completed write of " + data.getName() + " to repository " + repository.getName() +
                        " at simulated time: " + endTime);
            }
        };

        return startTime + calculateTransferTime(data);
    }

    /**
     * Calculates the time required to transfer a data object to the repository.
     *
     * @param data the data object
     * @return the transfer time in time units
     */
    private long calculateTransferTime(DataObject data) {
        return data.getSize() / 1024; // Example rate: 1 KB per time unit
    }
}
