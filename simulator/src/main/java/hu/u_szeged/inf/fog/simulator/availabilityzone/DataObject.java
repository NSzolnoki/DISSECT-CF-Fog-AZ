package hu.u_szeged.inf.fog.simulator.availabilityzone;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

/**
 * Represents a data object in the simulation, containing metadata such as name and size.
 * Provides functionality to convert the data object into a {@link StorageObject}.
 */
public class DataObject {
    private String name;
    private long size; // Size in bytes

    /**
     * Constructs a DataObject with the specified name and size.
     *
     * @param name the name of the data object
     * @param size the size of the data object in bytes
     */
    public DataObject(String name, long size) {
        this.name = name;
        this.size = size;
    }

    /**
     * Gets the name of the data object.
     *
     * @return the name of the data object
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the size of the data object.
     *
     * @return the size of the data object in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Converts this DataObject into a {@link StorageObject} for use in repository operations.
     *
     * @return a new {@link StorageObject} representing this data object
     */
    public StorageObject toStorageObject() {
        return new StorageObject(name, size, false);
    }
}
