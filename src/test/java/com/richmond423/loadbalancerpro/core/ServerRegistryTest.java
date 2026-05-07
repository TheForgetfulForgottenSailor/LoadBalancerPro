package com.richmond423.loadbalancerpro.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ServerRegistryTest {

    @Test
    void addRegistersServerInListAndMap() {
        ServerRegistry registry = new ServerRegistry();
        Server server = server("S1", ServerType.ONSITE);

        registry.add(server);

        assertFalse(registry.isEmpty());
        assertTrue(registry.contains("S1"));
        assertSame(server, registry.get("S1"));
        assertEquals(List.of(server), registry.snapshot());
        assertEquals(Map.of("S1", server), registry.mapSnapshot());
    }

    @Test
    void directDuplicateRegistrationAppendsToListAndMapsLatestServer() {
        ServerRegistry registry = new ServerRegistry();
        Server original = server("DUP", ServerType.ONSITE);
        Server replacement = server("DUP", ServerType.CLOUD);

        registry.add(original);
        registry.add(replacement);

        assertEquals(List.of(original, replacement), registry.snapshot());
        assertEquals(1, registry.mapSnapshot().size());
        assertSame(replacement, registry.get("DUP"));
        assertSame(replacement, registry.mapSnapshot().get("DUP"));
    }

    @Test
    void removeExistingServerUpdatesListAndMapViews() {
        ServerRegistry registry = new ServerRegistry();
        Server removed = server("REMOVE", ServerType.CLOUD);
        Server remaining = server("KEEP", ServerType.ONSITE);
        registry.add(removed);
        registry.add(remaining);

        registry.remove(removed);

        assertFalse(registry.contains("REMOVE"));
        assertNull(registry.get("REMOVE"));
        assertEquals(List.of(remaining), registry.snapshot());
        assertEquals(Map.of("KEEP", remaining), registry.mapSnapshot());
        assertEquals(List.of(remaining), registry.byType(ServerType.ONSITE));
        assertTrue(registry.byType(ServerType.CLOUD).isEmpty());
    }

    @Test
    void removeUnregisteredServerIsSafeNoOpForExistingState() {
        ServerRegistry registry = new ServerRegistry();
        Server existing = server("EXISTING", ServerType.ONSITE);
        registry.add(existing);

        registry.remove(server("MISSING", ServerType.CLOUD));

        assertEquals(List.of(existing), registry.snapshot());
        assertEquals(Map.of("EXISTING", existing), registry.mapSnapshot());
        assertSame(existing, registry.get("EXISTING"));
    }

    @Test
    void snapshotIsDetachedAndMapSnapshotIsUnmodifiableCopy() {
        ServerRegistry registry = new ServerRegistry();
        Server first = server("S1", ServerType.ONSITE);
        Server second = server("S2", ServerType.CLOUD);
        registry.add(first);

        List<Server> serverSnapshot = registry.snapshot();
        Map<String, Server> mapSnapshot = registry.mapSnapshot();

        serverSnapshot.clear();
        assertThrows(UnsupportedOperationException.class, () -> mapSnapshot.put("S2", second));

        registry.add(second);

        assertTrue(serverSnapshot.isEmpty());
        assertEquals(1, mapSnapshot.size());
        assertEquals(List.of(first, second), registry.snapshot());
        assertEquals(Map.of("S1", first, "S2", second), registry.mapSnapshot());
    }

    @Test
    void byTypeAndHealthySnapshotReflectCurrentServerState() {
        ServerRegistry registry = new ServerRegistry();
        Server onsite = server("ONSITE", ServerType.ONSITE);
        Server cloud = server("CLOUD", ServerType.CLOUD);
        Server unhealthyCloud = server("UNHEALTHY", ServerType.CLOUD);
        unhealthyCloud.setHealthy(false);

        registry.add(onsite);
        registry.add(cloud);
        registry.add(unhealthyCloud);

        assertEquals(List.of(onsite), registry.byType(ServerType.ONSITE));
        assertEquals(List.of(cloud, unhealthyCloud), registry.byType(ServerType.CLOUD));
        assertEquals(List.of(onsite, cloud), registry.healthySnapshot());
    }

    @Test
    void addRemoveSequencesPreserveRegistryInvariants() {
        ServerRegistry registry = new ServerRegistry();
        Server first = server("S1", ServerType.ONSITE);
        Server second = server("S2", ServerType.CLOUD);
        Server third = server("S3", ServerType.ONSITE);

        registry.add(first);
        registry.add(second);
        registry.remove(first);
        registry.add(third);
        registry.remove(second);

        assertTrue(registry.contains("S3"));
        assertFalse(registry.contains("S1"));
        assertFalse(registry.contains("S2"));
        assertSame(third, registry.get("S3"));
        assertEquals(List.of(third), registry.snapshot());
        assertEquals(Map.of("S3", third), registry.mapSnapshot());
        assertEquals(List.of(third), registry.byType(ServerType.ONSITE));
        assertTrue(registry.byType(ServerType.CLOUD).isEmpty());
        assertEquals(List.of(third), registry.healthySnapshot());
    }

    private Server server(String id, ServerType serverType) {
        return new Server(id, 10.0, 20.0, 30.0, serverType);
    }
}
