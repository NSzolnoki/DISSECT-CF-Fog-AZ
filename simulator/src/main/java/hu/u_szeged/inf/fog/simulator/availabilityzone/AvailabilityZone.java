package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;

public class AvailabilityZone {
    private Repository repository;
    private PhysicalMachine pm;
    private double latitude;
    private double longitude;
    private String name;

    public AvailabilityZone(String name, Repository repository, PhysicalMachine pm, double latitude, double longitude) {
        this.name = name;
        this.repository = repository;
        this.pm = pm;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public PhysicalMachine getPm() {
        return pm;
    }

    public String getName() {
        return name;
    }

    public double getLoad() {
        double pmAavailableCpu = this.pm.availableCapacities.getRequiredCPUs();
        double pmGetCpu = this.pm.getCapacities().getRequiredCPUs();
        return pmAavailableCpu / pmGetCpu;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public boolean isAvailable() {
        return pm.getState() == PhysicalMachine.State.RUNNING;
    }

    public double calculateDistance(double userLatitude, double userLongitude) {
        // Haversine formula to calculate distance in km
        double latDiff = Math.toRadians(latitude - userLatitude);
        double lonDiff = Math.toRadians(longitude - userLongitude);
        double a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2) +
                Math.cos(Math.toRadians(userLatitude)) * Math.cos(Math.toRadians(latitude)) *
                        Math.sin(lonDiff / 2) * Math.sin(lonDiff / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c; // Earth’s radius in km
    }

    public void readData(UserRequest request, DataObject data) {
        // Check if the data exists in the repository
        if (repository.lookup(data.getName()) == null) {
            System.out.println("Data " + data.getName() + " not found in repository " + repository.getName());
            return;
        }

        System.out.println("Initiating read of " + data.getName() + " from repository " + repository.getName());

        try {
            // Simulate fetching the object to memory
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

    public long writeData(UserRequest request, DataObject data, long previousCompletionTime) {
        // Wait until the previous completion time
        while (Timed.getFireCount() < previousCompletionTime) {
            Timed.simulateUntilLastEvent();
        }

        System.out.println("Initiating write of " + data.getName() + " to repository " + repository.getName());

        // Attempt to register the data in the repository
        boolean success = repository.registerObject(data.toStorageObject());
        if (!success) {
            System.err.println("Failed to write data " + data.getName() + ": not enough space in repository.");
            return previousCompletionTime;
        }

        long startTime = Timed.getFireCount();

        // Simulate transfer to storage
        new DeferredEvent(calculateTransferTime(data)) {
            @Override
            protected void eventAction() {
                long endTime = Timed.getFireCount();
                System.out.println("Completed write of " + data.getName() + " to repository " + repository.getName() +
                        " at simulated time: " + endTime);
            }
        };

        // Return the expected completion time of this write
        return startTime + calculateTransferTime(data);
    }

    private long calculateTransferTime(DataObject data) {
        return data.getSize() / 1024; // Example rate: 1 KB per time unit
    }
}