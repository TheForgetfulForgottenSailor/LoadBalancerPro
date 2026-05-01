package api;

import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import core.LoadBalancer;
import core.LoadDistributionResult;
import core.LaseShadowEventLog;
import core.LaseShadowObservabilitySnapshot;
import core.ScalingRecommendation;
import core.Server;
import core.ServerType;

@Service
public class AllocatorService {
    private static final String LASE_SHADOW_PROPERTY = "loadbalancerpro.lase.shadow.enabled";
    private static final String LASE_SHADOW_ENVIRONMENT_VARIABLE = "LOADBALANCERPRO_LASE_SHADOW_ENABLED";

    private final boolean laseShadowEnabled;
    private final LaseShadowEventLog laseShadowEventLog = new LaseShadowEventLog();

    public AllocatorService(Environment environment) {
        this.laseShadowEnabled = resolveLaseShadowEnabled(environment);
    }

    public AllocationResponse capacityAware(AllocationRequest request) {
        return allocate(request, true);
    }

    public AllocationResponse predictive(AllocationRequest request) {
        return allocate(request, false);
    }

    public LaseShadowObservabilitySnapshot laseShadowObservability() {
        return laseShadowEventLog.snapshot();
    }

    private AllocationResponse allocate(AllocationRequest request, boolean capacityAware) {
        validateRequest(request);
        LoadBalancer balancer = createLoadBalancer();
        try {
            for (ServerInput input : request.servers()) {
                balancer.addServer(toServer(input));
            }
            LoadDistributionResult result = capacityAware
                    ? balancer.capacityAwareWithResult(request.requestedLoad())
                    : balancer.predictiveLoadBalancingWithResult(request.requestedLoad());
            ScalingRecommendation recommendation = balancer.recommendScaling(
                    result.unallocatedLoad(), averageHealthyCapacity(request.servers()));
            ScalingSimulationResult simulation = simulateScaling(result.unallocatedLoad(), recommendation);
            return new AllocationResponse(
                    result.allocations(),
                    result.unallocatedLoad(),
                    recommendation.additionalServers(),
                    simulation);
        } finally {
            balancer.shutdown();
        }
    }

    static boolean resolveLaseShadowEnabled(Environment environment) {
        if (environment == null) {
            return false;
        }
        String configured = environment.getProperty(LASE_SHADOW_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = environment.getProperty(LASE_SHADOW_ENVIRONMENT_VARIABLE);
        }
        return Boolean.parseBoolean(configured);
    }

    boolean isLaseShadowEnabledForTesting() {
        return laseShadowEnabled;
    }

    private LoadBalancer createLoadBalancer() {
        return new LoadBalancer(laseShadowEnabled, laseShadowEventLog);
    }

    private static ScalingSimulationResult simulateScaling(
            double unallocatedLoad, ScalingRecommendation recommendation) {
        String reason;
        if (recommendation.additionalServers() > 0) {
            reason = "Unallocated load exceeds available capacity; simulated scale-up recommended.";
        } else if (unallocatedLoad > 0.0) {
            reason = "Unallocated load exists, but target capacity is unavailable; no scale-up count simulated.";
        } else {
            reason = "No unallocated load requiring scale-up.";
        }
        return new ScalingSimulationResult(recommendation.additionalServers(), reason, true);
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
