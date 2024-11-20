package hu.u_szeged.inf.fog.simulator.demo.AZExamples;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.availabilityzone.AvailabilityZone;
import hu.u_szeged.inf.fog.simulator.availabilityzone.Region;
import hu.u_szeged.inf.fog.simulator.availabilityzone.Region.SelectionStrategy;

public class ZoneSimulation_DisableClosest_Example {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting AWS-like Availability Zone simulation...");

        // Setup power transitions for Availability Zones
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator
                .generateTransitions(0.065, 1.475, 2.0, 1, 2);

        List<AvailabilityZone> zones = new ArrayList<>();

        // Define names and coordinates for each Availability Zone
        Object[][] locations = {
                { "Budapest", 47.4979, 19.0402 },
                { "Paris", 48.8566, 2.3522 },
                { "London", 51.5074, -0.1278 },
                { "New York", 40.7128, -74.0060 },
                { "Tokyo", 35.6895, 139.6917 }
        };

        // Create a user repository and PM to simulate the user's device
        Map<String, Integer> latencyMap = new HashMap<>();
        int latency = new Random().nextInt(300 - 30 + 1) + 30;
        latencyMap.put("UserRepo", latency);

        // Create AZs with repositories and physical machines
        for (int i = 0; i < locations.length; i++) {
            String name = (String) locations[i][0];
            double latitude = (double) locations[i][1];
            double longitude = (double) locations[i][2];

            String repoName = "AZ" + (i + 1) + "Repo";
            Repository repo = new Repository(4_294_967_296L, repoName, 3250, 3250, 3250,
                    latencyMap, transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network));

            PhysicalMachine pm = new PhysicalMachine(4, 0.01, 8_000_000L, repo, 10_000, 10_000,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            pm.turnon();
            while (pm.getState() != PhysicalMachine.State.RUNNING) {
                Timed.simulateUntilLastEvent();
            }
            System.out.println("PM State after creation and turning on: " + pm.getState());
            latency = new Random().nextInt(300 - 30 + 1) + 30;
            latencyMap.put(repoName, latency);
            zones.add(new AvailabilityZone(name, repo, pm, latitude, longitude));
        }

        Repository userRepo = new Repository(4_294_967_296L, "UserRepo", 3250, 3250, 3250,
                latencyMap, transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                transitions.get(PowerTransitionGenerator.PowerStateKind.network));

        PhysicalMachine userPm = new PhysicalMachine(4, 0.01, 8_000_000L, userRepo, 10_000, 10_000,
                transitions.get(PowerTransitionGenerator.PowerStateKind.host));        
        

        // Turn on the user's physical machine
        userPm.turnon();
        while (userPm.getState() != PhysicalMachine.State.RUNNING) {
            Timed.simulateUntilLastEvent();
        }
        System.out.println("User PM State after creation and turning on: " + userPm.getState());
        Region region = new Region(zones, SelectionStrategy.NEAREST);

        // Define user coordinates
        double userLatitude = 46.2530;
        double userLongitude = 20.1414;

        // Create a DataObject
        StorageObject file = new StorageObject("TopSecretData", 10000L, false);
        if (!userRepo.registerObject(file)) {
            System.err.println("Failed to register object in the user's repository.");
            return;
        }

        // Write Request: Store data in the first AZ
        region.handleWriteRequest(userRepo, file, userLatitude, userLongitude);

        // Wait until the write operation completes across all AZs
        while (!region.isDataAvailableInAllAZs(file.id)) {
            Timed.simulateUntilLastEvent();
        }

        // First Read Request
        System.out.println("\nFirst read request:");
        region.handleReadRequest(userRepo, file, userLatitude, userLongitude);

        // Make Budapest AZ unavailable
        for (AvailabilityZone zone : zones) {
            if (zone.getName().equals("Budapest")) {
                zone.getPm().switchoff(null);
                System.out.println("\nBudapest AZ has been made unavailable.");
                break;
            }
        }

        // Second Read Request
        System.out.println("\nSecond read request:");
        region.handleReadRequest(userRepo, file, userLatitude, userLongitude);

        System.out.println("Running simulation...");
        Timed.simulateUntilLastEvent();

        System.out.println("AWS-like Availability Zone simulation completed.");
    }
}
