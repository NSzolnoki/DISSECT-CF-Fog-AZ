/**
 * Represents a physical machine with an associated geographical location.
 * Extends the {@link PhysicalMachine} class by adding location information
 * for use in simulations involving geographical constraints or proximity-based operations.
 */
package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.u_szeged.inf.fog.simulator.availabilityzone.Locations.Location;

import java.util.Map;

public class PhysicalMachineWithLocation extends PhysicalMachine {
    private final Location location;

    /**
     * Constructs a {@code PhysicalMachineWithLocation} instance.
     *
     * @param numCores     the number of CPU cores
     * @param powerPerCore the power consumption per core
     * @param memory       the amount of memory in bytes
     * @param repository   the local repository associated with the machine
     * @param readSpeed    the read speed of the repository in bytes per second
     * @param writeSpeed   the write speed of the repository in bytes per second
     * @param transitions  a map of power state transitions
     * @param location     the geographical location of the machine
     */

    public PhysicalMachineWithLocation(
            int numCores, double powerPerCore, long memory, Repository repository,
            int readSpeed, int writeSpeed, Map<String, PowerState> transitions,
            Location location) {
        super(numCores, powerPerCore, memory, repository, readSpeed, writeSpeed, transitions);
        this.location = location;
    }

    /**
     * @return the geographical location of the physical machine.
     */
    public Location getLocation() {
        return location;
    }

    /**
     * @return a string representation of the physical machine, including its
     *         location and state.
     */
    @Override
    public String toString() {
        return "PhysicalMachineWithLocation{" +
                "location=" + location +
                ", state=" + getState() +
                '}';
    }
}
