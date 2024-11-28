package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.u_szeged.inf.fog.simulator.availabilityzone.SelectionStrategyEnum.SelectionStrategy;

import java.util.HashMap;
import java.util.Map;

public class StatisticsCollector {
    private Map<String, Integer> writeRequestsPerAZ = new HashMap<>();
    private Map<String, Integer> readRequestsPerAZ = new HashMap<>();
    private Map<String, Long> totalWriteTimePerAZ = new HashMap<>();
    private Map<String, Long> totalReadTimePerAZ = new HashMap<>();
    private int totalWrites = 0;
    private int totalReads = 0;
    private long totalWriteTime = 0;
    private long totalReadTime = 0;

    // Track failed read operations
    private int totalReadFailures = 0;
    private Map<String, Integer> readFailuresPerAZ = new HashMap<>();
    private Map<String, Integer> readFailuresPerUser = new HashMap<>();

    // Track all read requests with user location and AZ
    private Map<String, Integer> readsUserToAZ = new HashMap<>();

    public void logWrite(String azName, long timeTaken) {
        writeRequestsPerAZ.put(azName, writeRequestsPerAZ.getOrDefault(azName, 0) + 1);
        totalWriteTimePerAZ.put(azName, totalWriteTimePerAZ.getOrDefault(azName, 0L) + timeTaken);
        totalWrites++;
        totalWriteTime += timeTaken;
    }

    public void logRead(String userCity, String azName, long timeTaken) {
        readRequestsPerAZ.put(azName, readRequestsPerAZ.getOrDefault(azName, 0) + 1);
        totalReadTimePerAZ.put(azName, totalReadTimePerAZ.getOrDefault(azName, 0L) + timeTaken);
        totalReads++;
        totalReadTime += timeTaken;

        // Combine User and AZ for specific tracking
        String userToAZKey = userCity + " -> " + azName;
        readsUserToAZ.put(userToAZKey, readsUserToAZ.getOrDefault(userToAZKey, 0) + 1);
    }

    public void logReadFailure(String userCity, String azName, String dataId) {
        totalReadFailures++;
        readFailuresPerUser.put(userCity, readFailuresPerUser.getOrDefault(userCity, 0) + 1);
        readFailuresPerAZ.put(azName, readFailuresPerAZ.getOrDefault(azName, 0) + 1);
    }

    public void logWriteFailure(String sourceRepoCity, String targetRepoCity, String dataId) {
        totalReadFailures++;
        readFailuresPerUser.put(sourceRepoCity, readFailuresPerUser.getOrDefault(sourceRepoCity, 0) + 1);
        readFailuresPerAZ.put(targetRepoCity, readFailuresPerAZ.getOrDefault(targetRepoCity, 0) + 1);
    }

    public void printStatistics(SelectionStrategy userStrategy, SelectionStrategy azStrategy) {
        System.out.println("========== Simulation Statistics ==========");
        System.out.println("AZ write strategy: " + azStrategy.toString());
        System.out.println("User read strategy: " + userStrategy.toString());
        System.out.println("Total Writes: " + totalWrites);
        System.out.println("Total Reads: " + totalReads);
        System.out.println("Total Read Failures: " + totalReadFailures);
        System.out.println("Average Write Time: " + (totalWrites > 0 ? totalWriteTime / totalWrites : 0) + " simulated seconds");
        System.out.println("Average Read Time: " + (totalReads > 0 ? totalReadTime / totalReads : 0) + " simulated seconds");

        System.out.println("\nWrite Requests per AZ:");
        writeRequestsPerAZ.forEach((az, count) -> 
            System.out.println("  " + az + ": " + count + " requests, Total Time: " + totalWriteTimePerAZ.get(az) + " simulated seconds"));

        System.out.println("\nRead Requests per AZ:");
        readRequestsPerAZ.forEach((az, count) -> 
            System.out.println("  " + az + ": " + count + " requests, Total Time: " + totalReadTimePerAZ.get(az) + " simulated seconds"));

        System.out.println("\nRead Requests per User -> AZ:");
        readsUserToAZ.forEach((userToAZ, count) -> 
            System.out.println("  " + userToAZ + ": " + count + " requests"));

        System.out.println("\nRead Failures per User:");
        readFailuresPerUser.forEach((user, count) -> 
            System.out.println("  " + user + ": " + count + " failures"));

        System.out.println("\nRead Failures per AZ:");
        readFailuresPerAZ.forEach((az, count) -> 
            System.out.println("  " + az + ": " + count + " failures"));
    }
}
