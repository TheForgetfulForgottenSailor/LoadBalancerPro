package api;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import core.LoadBalancer;
import core.LoadDistributionResult;
import core.ScalingRecommendation;
import core.Server;
import core.ServerType;

@Service
public class AllocatorService {
    public AllocationResponse capacityAware(AllocationRequest request) {
        return allocate(request, true);
    }

    public AllocationResponse predictive(AllocationRequest request) {
        return allocate(request, false);
    }

    private AllocationResponse allocate(AllocationRequest request, boolean capacityAware) {
        validateRequest(request);
        LoadBalancer balancer = new LoadBalancer();
        try {
            for (ServerInput input : request.servers()) {
                balancer.addServer(toServer(input));
            }
            LoadDistributionResult result = capacityAware
                    ? balancer.capacityAwareWithResult(request.requestedLoad())
                    : balancer.predictiveLoadBalancingWithResult(request.requestedLoad());
            ScalingRecommendation recommendation = balancer.recommendScaling(
                    result.unallocatedLoad(), averageHealthyCapacity(request.servers()));
            return new AllocationResponse(
                    result.allocations(), result.unallocatedLoad(), recommendation.additionalServers());
        } finally {
            balancer.shutdown();
        }
    }

    private static void validateRequest(AllocationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.requestedLoad() < 0 || !Double.isFinite(request.requestedLoad())) {
            throw new IllegalArgumentException("requestedLoad must be a finite non-negative number");
        }
        if (request.servers() == null || request.servers().isEmpty()) {
            throw new IllegalArgumentException("servers must contain at least one server");
        }
    }

    private static Server toServer(ServerInput input) {
        if (input == null) {
            throw new IllegalArgumentException("server input cannot be null");
        }
        Server server = new Server(
                input.id(), input.cpuUsage(), input.memoryUsage(), input.diskUsage(), ServerType.ONSITE);
        server.setCapacity(input.capacity());
        server.setWeight(input.weight());
        server.setHealthy(input.healthy());
        return server;
    }

    private static double averageHealthyCapacity(List<ServerInput> servers) {
        return servers.stream()
                .filter(ServerInput::healthy)
                .mapToDouble(ServerInput::capacity)
                .average()
                .orElse(0.0);
    }
}
