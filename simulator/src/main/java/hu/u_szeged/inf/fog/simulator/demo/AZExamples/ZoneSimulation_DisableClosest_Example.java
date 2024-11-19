package hu.u_szeged.inf.fog.simulator.demo.AZExamples;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.availabilityzone.AvailabilityZone;
import hu.u_szeged.inf.fog.simulator.availabilityzone.DataObject;
import hu.u_szeged.inf.fog.simulator.availabilityzone.Region;
import hu.u_szeged.inf.fog.simulator.availabilityzone.Region.SelectionStrategy;
import hu.u_szeged.inf.fog.simulator.availabilityzone.UserRequest;

public class ZoneSimulation_DisableClosest_Example {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting AWS-like Availability Zone simulation...");

        // Setup power transitions for Availability Zones
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = 
                PowerTransitionGenerator.generateTransitions(0.065, 1.475, 2.0, 1, 2);

        List<AvailabilityZone> zones = new ArrayList<>();
        
        // Define names and coordinates for each Availability Zone
        Object[][] locations = {
            {"Budapest", 47.4979, 19.0402},
            {"Paris", 48.8566, 2.3522},
            {"London", 51.5074, -0.1278},
            {"New York", 40.7128, -74.0060},
            {"Tokyo", 35.6895, 139.6917}
        };

        // Loop to create availability zones and turn on physical machines
        for (int i = 0; i < locations.length; i++) {
            // Extract name and coordinates
            String name = (String) locations[i][0];
            double latitude = (double) locations[i][1];
            double longitude = (double) locations[i][2];

            // Create repository and physical machine
            String repoName = "AZ" + (i + 1) + "Repo";
            Repository repo = new Repository(10_000_000L, repoName, 3250, 3250, 3250, 
                    new HashMap<>(), transitions.get(PowerTransitionGenerator.PowerStateKind.storage),
                    transitions.get(PowerTransitionGenerator.PowerStateKind.network));
            
            PhysicalMachine pm = new PhysicalMachine(4, 1, 8_000_000L, repo, 10_000, 10_000,
                    transitions.get(PowerTransitionGenerator.PowerStateKind.host));

            // Turn on the physical machine to make it available
            pm.turnon();

            // Wait until the physical machine reaches the RUNNING state
            while (pm.getState() != PhysicalMachine.State.RUNNING) {
                Timed.simulateUntilLastEvent();
            }
            System.out.println("PM State after creation and turning on: " + pm.getState());

            // Initialize AvailabilityZone with name, latitude, and longitude
            zones.add(new AvailabilityZone(name, repo, pm, latitude, longitude));
        }

        // Create a Region with the Availability Zones
        Region region = new Region(zones, SelectionStrategy.NEAREST);



        // Define user coordinates (example: Szeged)
        double userLatitude = 46.2530;
        double userLongitude = 20.1414;

        // Create a DataObject to simulate transfer
        DataObject file = new DataObject("SampleFile", 10240); // 10 KB file

        // Write Request: Store data redundantly across all AZs
        UserRequest writeRequest = new UserRequest("Write SampleFile", false);
        region.handleWriteRequest(writeRequest, file, userLatitude, userLongitude);

        // First Read Request: Retrieve data from the closest AZ (should be Budapest)
        UserRequest readRequest = new UserRequest("Read SampleFile", true);
        System.out.println("\nFirst read request:");
        region.handleReadRequest(readRequest, file, userLatitude, userLongitude);

        // Make the Budapest AZ unavailable
        for (AvailabilityZone zone : zones) {
            if (zone.getName().equals("Budapest")) {
                zone.getPm().switchoff(null);
                System.out.println("\nBudapest AZ has been made unavailable.");
                break;
            }
        }

        // Second Read Request: Attempt to read again, should select the next closest AZ
        System.out.println("\nSecond read request:");
        region.handleReadRequest(readRequest, file, userLatitude, userLongitude);

        System.out.println("Running simulation...");
        Timed.simulateUntilLastEvent();

        System.out.println("AWS-like Availability Zone simulation completed.");
    }
}
