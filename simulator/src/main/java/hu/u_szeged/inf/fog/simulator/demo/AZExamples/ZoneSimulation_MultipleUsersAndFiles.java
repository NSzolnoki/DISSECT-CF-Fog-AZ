package hu.u_szeged.inf.fog.simulator.demo.AZExamples;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.availabilityzone.AvailabilityZone;
import hu.u_szeged.inf.fog.simulator.availabilityzone.Region;
import hu.u_szeged.inf.fog.simulator.availabilityzone.Region.SelectionStrategy;
import hu.u_szeged.inf.fog.simulator.availabilityzone.Locations;
import hu.u_szeged.inf.fog.simulator.availabilityzone.PhysicalMachineWithLocation;

public class ZoneSimulation_MultipleUsersAndFiles {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting AWS-like Availability Zone simulation...");

        // Setup power transitions for Availability Zones
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator
                .generateTransitions(0.065, 1.475, 2.0, 1, 2);

        List<AvailabilityZone> zones = new ArrayList<>();
        List<Repository> allRepositories = new ArrayList<>(); // To maintain all repositories

        // Generate AZs with repositories and physical machines
        Random random = new Random();

        for (int i = 0; i < 10; i++) {
            Locations.Location randomLocation = Locations.getRandomLocation();

            String repoName = randomLocation.getCity() + " Repo";
            Repository repo = new Repository(4_294_967_296L, repoName, 3250, 3250, 3250,
                    new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachineWithLocation pm = new PhysicalMachineWithLocation(4, 0.01, 8_000_000L, repo, 10_000, 10_000,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.host), randomLocation);

            pm.turnon();
            while (pm.getState() != PhysicalMachineWithLocation.State.RUNNING) {
                Timed.simulateUntilLastEvent();
            }
            System.out.println("PM State after creation and turning on: " + pm.getState());
            zones.add(new AvailabilityZone(randomLocation.getCity(), repo, pm, randomLocation));
            allRepositories.add(repo); // Add to the global repository list
        }

        // Generate 50 users with their own repositories
        List<Repository> userRepos = new ArrayList<>();
        List<PhysicalMachineWithLocation> userPMs = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Locations.Location randomLocation = Locations.getRandomLocation();
            String userRepoName = "UserRepo" + i;
            Repository userRepo = new Repository(4_294_967_296L, userRepoName, 3250, 3250, 3250,
                    new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network));

                    PhysicalMachineWithLocation userPm = new PhysicalMachineWithLocation(4, 0.01, 8_000_000L, userRepo, 10_000, 10_000,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.host), randomLocation);

            userPm.turnon();
            while (userPm.getState() != PhysicalMachineWithLocation.State.RUNNING) {
                Timed.simulateUntilLastEvent();
            }
            System.out.println("User PM " + i + " State after creation and turning on: " + userPm.getState());
            userRepos.add(userRepo);
            userPMs.add(userPm);
            allRepositories.add(userRepo); // Add to the global repository list
        }

        // Assign random latencies between all repositories
        assignLatencies(allRepositories, random);

        Region region = new Region(zones, SelectionStrategy.NEAREST);

        // Generate 100 files randomly
        List<StorageObject> files = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            StorageObject file = new StorageObject("File" + i, random.nextInt(10_000) + 1_000L, false);
            files.add(file);
        }

        // Randomly assign files to users and simulate read/write operations
        for (int i = 0; i < 100; i++) {
            PhysicalMachineWithLocation randomUserPm = userPMs.get(random.nextInt(userPMs.size()));
            StorageObject randomFileToWrite = files.get(random.nextInt(files.size()));

            // Register file in the user's repository
            if (!randomUserPm.localDisk.registerObject(randomFileToWrite)) {
                System.err.println("Failed to register object " + randomFileToWrite.id + " in " + randomUserPm.getLocation().getCity());
                continue;
            }            
            // Perform a write operation
            region.handleWriteRequest(randomUserPm, randomFileToWrite);

            // Wait until the write operation completes across all AZs
            while (!region.isDataAvailableInAllAZs(randomFileToWrite)) {
                Timed.simulateUntilLastEvent();
            }

            List<StorageObject> availableStorageObjects = region.getAvailableObjects();
            StorageObject randomFileToRead = availableStorageObjects.get(random.nextInt(availableStorageObjects.size()));
            // Perform a read operation
            long readCompletionTime = region.handleReadRequest(randomUserPm, randomFileToRead);

            // Wait for the read to complete
            while (Timed.getFireCount() < readCompletionTime) {
                Timed.simulateUntilLastEvent();
            }
        }

        // Simulate disabling an AZ and retrying reads
        for (AvailabilityZone zone : zones) {
            if (zone.getName().equals("Budapest")) {
                zone.getPm().switchoff(null);
                System.out.println("\nBudapest AZ has been made unavailable.");
                break;
            }
        }

        // Retry reads after disabling AZ
        for (int i = 0; i < 10; i++) { // Retry for 10 random files
            PhysicalMachineWithLocation randomUserPm = userPMs.get(random.nextInt(userPMs.size()));
            List<StorageObject> availableStorageObjects = region.getAvailableObjects();
            StorageObject randomFileToRead = availableStorageObjects.get(random.nextInt(availableStorageObjects.size()));

            long readCompletionTime = region.handleReadRequest(randomUserPm, randomFileToRead);

            while (Timed.getFireCount() < readCompletionTime) {
                Timed.simulateUntilLastEvent();
            }
        }

        // Run remaining simulation events
        System.out.println("Running simulation...");
        Timed.simulateUntilLastEvent();

        System.out.println("AWS-like Availability Zone simulation completed.");
    }

    private static void assignLatencies(List<Repository> repositories, Random random) {
        for (Repository repo : repositories) {
            for (Repository otherRepo : repositories) {
                if (!repo.equals(otherRepo)) {
                    repo.addLatencies(otherRepo.getName(), random.nextInt(300 - 30 + 1) + 30);
                }
            }
        }
    }
    
}
