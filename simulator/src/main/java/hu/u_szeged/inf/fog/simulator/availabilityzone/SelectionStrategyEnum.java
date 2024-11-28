package hu.u_szeged.inf.fog.simulator.availabilityzone;

public class SelectionStrategyEnum {

    public enum SelectionStrategy {
        NEAREST,         // Select the closest AZ to the user
        LEAST_LOADED,    // Select the AZ with the lowest load
        RANDOM,          // Select a random AZ
        MOST_RECENTLY_USED, // Select the AZ that was most recently used
        LOWEST_LATENCY
    }
}
