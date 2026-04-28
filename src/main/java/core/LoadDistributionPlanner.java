package core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LoadDistributionPlanner {
    private LoadDistributionPlanner() {
    }

    static Map<String, Double> roundRobin(List<Server> healthyServers, double totalData) {
        Map<String, Double> distribution = new HashMap<>();
        double dataPerServer = totalData / healthyServers.size();
        for (Server server : healthyServers) {
            distribution.put(server.getServerId(), dataPerServer);
        }
        return distribution;
    }
}
