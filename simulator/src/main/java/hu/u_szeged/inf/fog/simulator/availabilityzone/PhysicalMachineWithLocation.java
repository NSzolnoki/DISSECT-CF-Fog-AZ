package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.u_szeged.inf.fog.simulator.availabilityzone.Locations.Location;

import java.util.Map;

public class PhysicalMachineWithLocation extends PhysicalMachine {
    private final Location location;

    public PhysicalMachineWithLocation(
            int numCores, double powerPerCore, long memory, Repository repository,
            int readSpeed, int writeSpeed, Map<String, PowerState> transitions,
            Location location) {
        super(numCores, powerPerCore, memory, repository, readSpeed, writeSpeed, transitions);
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return "PhysicalMachineWithLocation{" +
                "location=" + location +
                ", state=" + getState() +
                '}';
    }
}
