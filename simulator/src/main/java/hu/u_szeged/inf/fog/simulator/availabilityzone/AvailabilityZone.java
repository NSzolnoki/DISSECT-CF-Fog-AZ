package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;

public class AvailabilityZone {
    private String name;
    private Repository repository;
    private PhysicalMachineWithLocation pm;
    private Locations.Location location; // Use the Location class here

    public AvailabilityZone(String name, Repository repository, PhysicalMachineWithLocation pm,
            Locations.Location location) {
        this.name = name;
        this.repository = repository;
        this.pm = pm;
        this.location = location;
    }

    public Repository getRepository() {
        return repository;
    }

    public PhysicalMachineWithLocation getPm() {
        return pm;
    }

    public String getName() {
        return name;
    }

    public double getLoad() {
        double pmAvailableCpu = this.pm.availableCapacities.getRequiredCPUs();
        double pmTotalCpu = this.pm.getCapacities().getRequiredCPUs();
        return pmAvailableCpu / pmTotalCpu;
    }

    public Locations.Location getLocation() {
        return location;
    }

    public boolean isAvailable() {
        return pm.getState() == PhysicalMachineWithLocation.State.RUNNING;
    }

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

    public long readData(PhysicalMachineWithLocation userPm, StorageObject data, AvailabilityZone selectedZone) {
        if (selectedZone.getRepository().lookup(data.id) == null) {
            System.err
                    .println("Data " + data.id + " not found in repository " + selectedZone.getRepository().getName());
            return Timed.getFireCount();
        }
        Repository userRepo = userPm.localDisk;
        if (userRepo.lookup(data.id) != null) {
            System.out.println("Removing existing data " + data.id + " from user repository: " + userRepo.getName());
            userRepo.deregisterObject(data);
        }

        if (userPm.localDisk.getFreeStorageCapacity() < data.size) {
            System.err.println("Failed to initiate download for data " + data.id +
                    ": Not enough space on the user repo.");
            return Timed.getFireCount();
        }

        System.out.println(
                "Initiating download of " + data.id + " from repository " + selectedZone.getRepository().getName());

        try {
            ResourceConsumption consumption = selectedZone.getRepository().requestContentDelivery(
                    data.id,
                    userRepo,
                    new ResourceConsumption.ConsumptionEvent() {
                        @Override
                        public void conComplete() {
                            long endTime = Timed.getFireCount();
                            System.out.println("Download of " + data.id + " completed from repository " +
                                    selectedZone.getLocation().getCity() + " to user repository at "
                                    + userPm.getLocation().getCity() + " simulated time: " + endTime);
                        }

                        @Override
                        public void conCancelled(ResourceConsumption problematic) {
                            System.err.println("Download of " + data.id + " from repository " +
                                    selectedZone.getRepository().getName() + " to user repository at "
                                    + userPm.getLocation().getCity() + " was cancelled.");
                        }
                    });

            if (consumption == null) {
                System.err.println("Failed to initiate download for data " + data.id +
                        ": Network error.");
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

    public long writeData(Repository userRepo, StorageObject data, long previousCompletionTime) {
        // Wait until the previous completion time
        while (Timed.getFireCount() < previousCompletionTime) {
            Timed.simulateUntilLastEvent();
        }

        System.out.println("Initiating write of " + data.id + " to repository " + repository.getName());

        // Check for free capacity before attempting to write
        if (repository.getFreeStorageCapacity() < data.size) {
            System.err.println("Not enough space in repository for data: " + data.id +
                    "\nFree space: " + repository.getFreeStorageCapacity());
            return previousCompletionTime;
        }

        // Check if the object already exists
        if (repository.lookup(data.id) != null) {
            System.err.println("Object " + data.id + " already exists in the repository.");
            return previousCompletionTime;
        }

        try {
            // Request content delivery from the user repository to this AZ repository
            ResourceConsumption consumption = userRepo.requestContentDelivery(
                    data.id,
                    repository,
                    new ResourceConsumption.ConsumptionEvent() {
                        @Override
                        public void conComplete() {
                            long endTime = Timed.getFireCount();
                            System.out.println("Completed write of " + data.id +
                                    " to repository " + repository.getName() +
                                    " at simulated time: " + endTime);
                        }

                        @Override
                        public void conCancelled(ResourceConsumption problematic) {
                            System.err.println("Write of " + data.id + " to repository " + repository.getName() +
                                    " was cancelled.");
                        }
                    });

            if (consumption == null) {
                System.err.println("Failed to initiate write for data " + data.id +
                        ": Not enough space or already exists.");
                return previousCompletionTime;
            }

            // Wait until the write operation is complete
            long startTime = Timed.getFireCount();
            long completionTime = startTime + consumption.getCompletionDistance();
            while (Timed.getFireCount() < completionTime) {
                Timed.simulateUntilLastEvent();
            }

            // Verify the data has been written
            if (repository.lookup(data.id) == null) {
                System.err.println("Data write completed, but object " + data.id + " is not found in the repository.");
            } else {
                System.out.println("Data " + data.id + " successfully written to " + repository.getName());
            }

            return completionTime;

        } catch (NetworkException e) {
            System.err.println("Failed to write data " + data.id + " due to network issues.");
            e.printStackTrace();
            return previousCompletionTime;
        }
    }

}
