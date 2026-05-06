package com.richmond423.loadbalancerpro.core;

import com.richmond423.loadbalancerpro.util.Utils;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.Logger;

final class ConsistentHashRing {
    private final ConcurrentNavigableMap<Long, Server> ring = new ConcurrentSkipListMap<>();
    private final int hashReplicas;
    private final Logger logger;

    ConsistentHashRing(int hashReplicas, Logger logger) {
        this.hashReplicas = Math.max(1, hashReplicas);
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    void addServer(Server server) {
        for (int i = 0; i < hashReplicas; i++) {
            long hash = Utils.hash(server.getServerId() + "-" + i);
            if (hash == Long.MIN_VALUE) {
                logger.warn("Invalid hash for server {} replica {}; using fallback.", server.getServerId(), i);
                hash = i;
            }
            ring.put(hash, server);
        }
    }

    void removeServer(Server server) {
        for (int i = 0; i < hashReplicas; i++) {
            ring.remove(Utils.hash(server.getServerId() + "-" + i));
        }
    }

    boolean isEmpty() {
        return ring.isEmpty();
    }

    Server selectHealthyServer(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        long keyHash = Utils.hash(key);
        Map.Entry<Long, Server> entry = ring.ceilingEntry(keyHash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        Server server = entry.getValue();
        int attempts = 0;
        while (!server.isHealthy() && attempts < ring.size()) {
            entry = ring.higherEntry(entry.getKey());
            if (entry == null) {
                entry = ring.firstEntry();
            }
            server = entry.getValue();
            attempts++;
        }
        return server.isHealthy() ? server : null;
    }
}
