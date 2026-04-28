package api;

import java.util.List;

public record AllocationRequest(double requestedLoad, List<ServerInput> servers) {
}
