package api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AllocatorController {
    private static final String VERSION = "1.0.0";

    private final AllocatorService allocatorService;

    public AllocatorController(AllocatorService allocatorService) {
        this.allocatorService = allocatorService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "version", VERSION);
    }

    @PostMapping("/allocate/capacity-aware")
    public AllocationResponse capacityAware(@RequestBody AllocationRequest request) {
        return allocatorService.capacityAware(request);
    }

    @PostMapping("/allocate/predictive")
    public AllocationResponse predictive(@RequestBody AllocationRequest request) {
        return allocatorService.predictive(request);
    }
}
