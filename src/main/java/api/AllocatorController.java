package api;

import java.util.Map;

import core.LaseShadowObservabilitySnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AllocatorController {
    private final AllocatorService allocatorService;
    private final String version;

    public AllocatorController(
            AllocatorService allocatorService,
            @Value("${loadbalancerpro.app.version:${info.app.version:unknown}}") String version) {
        this.allocatorService = allocatorService;
        this.version = version;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "version", version);
    }

    @GetMapping("/lase/shadow")
    public LaseShadowObservabilitySnapshot laseShadowObservability() {
        return allocatorService.laseShadowObservability();
    }

    @PostMapping("/allocate/capacity-aware")
    public AllocationResponse capacityAware(@Valid @RequestBody AllocationRequest request) {
        return allocatorService.capacityAware(request);
    }

    @PostMapping("/allocate/predictive")
    public AllocationResponse predictive(@Valid @RequestBody AllocationRequest request) {
        return allocatorService.predictive(request);
    }
}
