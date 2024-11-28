package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.u_szeged.inf.fog.simulator.availabilityzone.SelectionStrategyEnum.SelectionStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * A class for collecting and managing statistics related to write and read operations
 * across Availability Zones (AZs) in a distributed system simulation.
 * 
 * The {@code StatisticsCollector} tracks:
 * <ul>
 *   <li>Total number of write and read operations</li>
 *   <li>Time taken for these operations</li>
 *   <li>Failures during read and write operations</li>
 *   <li>Mapping of requests between users and AZs</li>
 * </ul>
 * This information can be used to analyze and evaluate the performance of different
 * selection strategies for AZs.
 */
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

    /**
     * Logs a successful write operation to an AZ.
     * 
     * @param azName    the name of the AZ where the write operation occurred
     * @param timeTaken the simulated time taken for the write operation
     */
    public void logWrite(String azName, long timeTaken) {
        writeRequestsPerAZ.put(azName, writeRequestsPerAZ.getOrDefault(azName, 0) + 1);
        totalWriteTimePerAZ.put(azName, totalWriteTimePerAZ.getOrDefault(azName, 0L) + timeTaken);
        totalWrites++;
        totalWriteTime += timeTaken;
    }

    /**
     * Logs a successful read operation from an AZ by a user.
     * 
     * @param userCity  the city of the user who initiated the read request
     * @param azName    the name of the AZ from which the data was read
     * @param timeTaken the simulated time taken for the read operation
     */
    public void logRead(String userCity, String azName, long timeTaken) {
        readRequestsPerAZ.put(azName, readRequestsPerAZ.getOrDefault(azName, 0) + 1);
        totalReadTimePerAZ.put(azName, totalReadTimePerAZ.getOrDefault(azName, 0L) + timeTaken);
        totalReads++;
        totalReadTime += timeTaken;

        // Combine User and AZ for specific tracking
        String userToAZKey = userCity + " -> " + azName;
        readsUserToAZ.put(userToAZKey, readsUserToAZ.getOrDefault(userToAZKey, 0) + 1);
    }

    /**
     * Logs a failed read operation by a user.
     * 
     * @param userCity the city of the user who initiated the failed read request
     * @param azName   the name of the AZ where the failure occurred
     * @param dataId   the ID of the data that could not be read
     */
    public void logReadFailure(String userCity, String azName, String dataId) {
        totalReadFailures++;
        readFailuresPerUser.put(userCity, readFailuresPerUser.getOrDefault(userCity, 0) + 1);
        readFailuresPerAZ.put(azName, readFailuresPerAZ.getOrDefault(azName, 0) + 1);
    }

    /**
     * Logs a failed write operation between two repositories.
     * 
     * @param sourceRepoCity the city of the repository initiating the write request
     * @param targetRepoCity the city of the repository where the write failed
     * @param dataId         the ID of the data that could not be written
     */
    public void logWriteFailure(String sourceRepoCity, String targetRepoCity, String dataId) {
        totalReadFailures++;
        readFailuresPerUser.put(sourceRepoCity, readFailuresPerUser.getOrDefault(sourceRepoCity, 0) + 1);
        readFailuresPerAZ.put(targetRepoCity, readFailuresPerAZ.getOrDefault(targetRepoCity, 0) + 1);
    }

    /**
     * Prints a detailed report of the collected statistics, including information
     * about read and write operations, failures, and selection strategies.
     * 
     * @param userStrategy the selection strategy used for user read operations
     * @param azStrategy   the selection strategy used for AZ write operations
     */
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
