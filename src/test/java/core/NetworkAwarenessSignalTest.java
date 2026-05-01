package core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class NetworkAwarenessSignalTest {
    private static final Instant NOW = Instant.parse("2026-04-30T12:00:00Z");

    @Test
    void neutralSignalHasZeroRiskInputs() {
        NetworkAwarenessSignal signal = NetworkAwarenessSignal.neutral("api", NOW);

        assertEquals("api", signal.targetId());
        assertEquals(0.0, signal.timeoutRate());
        assertEquals(0.0, signal.retryRate());
        assertEquals(0.0, signal.connectionFailureRate());
        assertEquals(0.0, signal.latencyJitterMillis());
        assertFalse(signal.recentErrorBurst());
        assertEquals(0, signal.requestTimeoutCount());
        assertEquals(0, signal.sampleSize());
        assertEquals(NOW, signal.timestamp());
    }

    @Test
    void validSignalPreservesApplicationLayerTransportSignals() {
        NetworkAwarenessSignal signal = new NetworkAwarenessSignal(
                "worker-1", 0.05, 0.10, 0.02, 12.5, true, 3, 100, NOW);

        assertEquals("worker-1", signal.targetId());
        assertEquals(0.05, signal.timeoutRate());
        assertEquals(0.10, signal.retryRate());
        assertEquals(0.02, signal.connectionFailureRate());
        assertEquals(12.5, signal.latencyJitterMillis());
        assertTrue(signal.recentErrorBurst());
        assertEquals(3, signal.requestTimeoutCount());
        assertEquals(100, signal.sampleSize());
    }

    @Test
    void invalidSignalsAreRejected() {
        assertAll("invalid network awareness signal",
                () -> assertInvalid(() -> signal(null, 0.0, 0.0, 0.0, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("   ", 0.0, 0.0, 0.0, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", -0.01, 0.0, 0.0, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 1.01, 0.0, 0.0, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", Double.NaN, 0.0, 0.0, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, -0.01, 0.0, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 1.01, 0.0, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, Double.NaN, 0.0, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 0.0, -0.01, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 0.0, 1.01, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 0.0, Double.NaN, 0.0, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 0.0, 0.0, -0.1, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 0.0, 0.0, Double.NaN, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, false, 0, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 0.0, 0.0, 0.0, false, -1, 0, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 0.0, 0.0, 0.0, false, 0, -1, NOW)),
                () -> assertInvalid(() -> signal("api", 0.0, 0.0, 0.0, 0.0, false, 0, 0, null))
        );
    }

    private static NetworkAwarenessSignal signal(String targetId,
                                                 double timeoutRate,
                                                 double retryRate,
                                                 double connectionFailureRate,
                                                 double latencyJitterMillis,
                                                 boolean recentErrorBurst,
                                                 int requestTimeoutCount,
                                                 int sampleSize,
                                                 Instant timestamp) {
        return new NetworkAwarenessSignal(targetId, timeoutRate, retryRate, connectionFailureRate,
                latencyJitterMillis, recentErrorBurst, requestTimeoutCount, sampleSize, timestamp);
    }

    private static void assertInvalid(Executable executable) {
        RuntimeException thrown = assertThrows(RuntimeException.class, executable);
        assertNotNull(thrown.getMessage());
        assertFalse(thrown.getMessage().isBlank());
    }
}
