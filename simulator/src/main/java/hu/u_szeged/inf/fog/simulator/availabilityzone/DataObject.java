package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

public class DataObject {
    private String name;
    private long size; // Size in bytes

    public DataObject(String name, long size) {
        this.name = name;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    // Method to convert DataObject to StorageObject
    public StorageObject toStorageObject() {
        return new StorageObject(name, size, false);
    }
}
