package hu.u_szeged.inf.fog.simulator.availabilityzone;

public class UserRequest {
    private String data;
    private boolean isReadRequest;

    public UserRequest(String data, boolean isReadRequest) {
        this.data = data;
        this.isReadRequest = isReadRequest;
    }

    public String getData() {
        return data;
    }

    public boolean isReadRequest() {
        return isReadRequest;
    }
}
