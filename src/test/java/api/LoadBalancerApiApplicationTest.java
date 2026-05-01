package api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadBalancerApiApplicationTest {
    @Test
    void laseDemoFlagSkipsApiServerStartup() {
        assertFalse(LoadBalancerApiApplication.shouldStartApi(new String[]{"--lase-demo"}));
        assertFalse(LoadBalancerApiApplication.shouldStartApi(new String[]{"--lase-demo=healthy"}));
        assertFalse(LoadBalancerApiApplication.shouldStartApi(new String[]{"--lase-replay=shadow-events.jsonl"}));
        assertTrue(LoadBalancerApiApplication.shouldStartApi(new String[]{"--server.port=18080"}));
        assertTrue(LoadBalancerApiApplication.shouldStartApi(new String[]{}));
    }
}
