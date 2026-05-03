package api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {
    private final RoutingComparisonService routingComparisonService;

    public RoutingController(RoutingComparisonService routingComparisonService) {
        this.routingComparisonService = routingComparisonService;
    }

    @PostMapping("/compare")
    public RoutingComparisonResponse compare(@Valid @RequestBody RoutingComparisonRequest request) {
        return routingComparisonService.compare(request);
    }
}
