package hu.u_szeged.inf.fog.simulator.availabilityzone;

/**
 * Represents a user request in the cloud simulation.
 * A user request specifies the data to be accessed and whether it is a read or write request.
 */
public class UserRequest {
    private String data;
    private boolean isReadRequest;

    /**
     * Constructs a UserRequest with the specified data and request type.
     *
     * @param data          the name or identifier of the data being accessed
     * @param isReadRequest true if the request is a read request, false if it is a write request
     */
    public UserRequest(String data, boolean isReadRequest) {
        this.data = data;
        this.isReadRequest = isReadRequest;
    }

    /**
     * Gets the name or identifier of the data associated with this request.
     *
     * @return the data name or identifier
     */
    public String getData() {
        return data;
    }

    /**
     * Checks if this request is a read request.
     *
     * @return true if the request is a read request, false otherwise
     */
    public boolean isReadRequest() {
        return isReadRequest;
    }
}
