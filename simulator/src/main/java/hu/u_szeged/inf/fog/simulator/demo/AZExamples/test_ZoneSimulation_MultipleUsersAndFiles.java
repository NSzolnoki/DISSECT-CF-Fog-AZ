package hu.u_szeged.inf.fog.simulator.demo.AZExamples;

import java.util.*;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.availabilityzone.*;
import hu.u_szeged.inf.fog.simulator.availabilityzone.SelectionStrategyEnum.SelectionStrategy;

public class test_ZoneSimulation_MultipleUsersAndFiles {

    // Global Random instance with a fixed seed for reproducibility
    private static final long SEED = 12345L; // You can change this seed for different random behaviors
    private static final Random GLOBAL_RANDOM = new Random(SEED);

    private static final SelectionStrategy SelectedAZStrategy = SelectionStrategy.NEAREST;
    private static final SelectionStrategy SelectedUserStrategy = SelectionStrategy.LOWEST_LATENCY;

    public static void main(String[] args) throws Exception {
        System.out.println("Starting AWS-like Availability Zone simulation...");

        // Setup power transitions for Availability Zones
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator
                .generateTransitions(0.065, 1.475, 2.0, 1, 2);

        List<AvailabilityZone> zones = new ArrayList<>();
        List<Repository> allRepositories = new ArrayList<>();

        System.out.println("========================== Creating and turning on AZ machines ==========================");
        for (int i = 0; i < 10; i++) {
            Locations.Location randomLocation = Locations.getRandomLocation(GLOBAL_RANDOM);

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
            allRepositories.add(repo);
        }

        System.out
                .println("========================== Creating and turning on User machines ==========================");
        List<Repository> userRepos = new ArrayList<>();
        List<PhysicalMachineWithLocation> userPMs = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Locations.Location randomLocation = Locations.getRandomLocation(GLOBAL_RANDOM);
            String userRepoName = "UserRepo" + i;
            Repository userRepo = new Repository(4_294_967_296L, userRepoName, 3250, 3250, 3250,
                    new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachineWithLocation userPm = new PhysicalMachineWithLocation(4, 0.01, 8_000_000L, userRepo, 10_000,
                    10_000,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.host), randomLocation);

            userPm.turnon();
            while (userPm.getState() != PhysicalMachineWithLocation.State.RUNNING) {
                Timed.simulateUntilLastEvent();
            }
            System.out.println("User PM " + i + " State after creation and turning on: " + userPm.getState());
            userRepos.add(userRepo);
            userPMs.add(userPm);
            allRepositories.add(userRepo);
        }

        Region region = new Region(zones, SelectedAZStrategy);
        // Assign random latencies between all repositories
        region.assignLatencies(allRepositories, GLOBAL_RANDOM);

        // Generate 100 files randomly
        List<StorageObject> files = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            StorageObject file = new StorageObject("File" + i, GLOBAL_RANDOM.nextInt(10_000) + 1_000L, false);
            files.add(file);
        }

        System.out.println(
                "========================== Asign 10 random files to 10 random users and simulate write and read ==========================");
        for (int i = 0; i < 10; i++) {
            PhysicalMachineWithLocation randomUserPm = userPMs.get(GLOBAL_RANDOM.nextInt(userPMs.size()));
            StorageObject randomFileToWrite = files.get(GLOBAL_RANDOM.nextInt(files.size()));

            if (!randomUserPm.localDisk.registerObject(randomFileToWrite)) {
                System.err.println("Failed to register object " + randomFileToWrite.id + " in "
                        + randomUserPm.getLocation().getCity());
                continue;
            }
            region.handleWriteRequest(randomUserPm, randomFileToWrite);

            while (!region.isDataAvailableInAllAZs(randomFileToWrite)) {
                Timed.simulateUntilLastEvent();
            }


            List<StorageObject> availableStorageObjects = region.getAvailableObjects();
            StorageObject randomFileToRead = availableStorageObjects
                    .get(GLOBAL_RANDOM.nextInt(availableStorageObjects.size()));
            long readCompletionTime = region.handleReadRequest(randomUserPm, randomFileToRead,
                    SelectedUserStrategy);

            while (Timed.getFireCount() < readCompletionTime) {
                Timed.simulateUntilLastEvent();
            }
        }

        System.out.println("========================== Turn off a random AZ in the Region ==========================");
        if (!zones.isEmpty()) {
            int randomIndex = GLOBAL_RANDOM.nextInt(zones.size());
            AvailabilityZone randomZone = zones.get(randomIndex);

            randomZone.getPm().switchoff(null);
            System.out.println("\n" + randomZone.getName() + " AZ has been made unavailable.");
        } else {
            System.out.println("No availability zones available to disable.");
        }

        System.out.println(
                "========================== Fire 10 concurrent reading from the AZ by random users and files ==========================");
        for (int i = 0; i < 10; i++) {
            PhysicalMachineWithLocation randomUserPm = userPMs.get(GLOBAL_RANDOM.nextInt(userPMs.size()));
            List<StorageObject> availableStorageObjects = region.getAvailableObjects();
            StorageObject randomFileToRead = availableStorageObjects
                    .get(GLOBAL_RANDOM.nextInt(availableStorageObjects.size()));

            region.handleReadRequest(randomUserPm, randomFileToRead, SelectedUserStrategy);
            Timed.simulateUntilLastEvent();
        }

        System.out.println("AWS-like Availability Zone simulation completed.");
        region.statisticsCollector.printStatistics(SelectedUserStrategy, SelectedAZStrategy);
    }

}
