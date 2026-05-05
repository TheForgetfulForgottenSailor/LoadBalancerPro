package com.richmond423.loadbalancerpro.core;

import com.richmond423.loadbalancerpro.core.CloudConfig;
import com.richmond423.loadbalancerpro.core.CloudManager;
import com.richmond423.loadbalancerpro.core.LoadBalancer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CloudManagerSafetyTest {
    private static final String ACCESS_KEY = "UNIT_TEST_ACCESS_KEY_ID";
    private static final String SECRET_KEY = "UNIT_TEST_SECRET_ACCESS_KEY";

    @Test
    void defaultConfigIsDryRunAndDoesNotProvisionServers() throws Exception {
        LoadBalancer balancer = new LoadBalancer();
        CloudManager manager = new CloudManager(
            balancer,
            new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-placeholder", "subnet-placeholder"),
            null);

        manager.initializeCloudServers(1, 2);
        manager.scaleServers(2);
        manager.updateServerMetricsFromCloud();
        manager.shutdown();

        assertTrue(balancer.getServers().isEmpty(), "Dry-run cloud operations must not provision servers.");
    }

    @Test
    void placeholderCredentialsFailClosed() {
        assertThrows(IllegalArgumentException.class,
            () -> new CloudConfig("mock_access_key", SECRET_KEY, "us-east-1", "lt-placeholder", "subnet-placeholder"));
        assertThrows(IllegalArgumentException.class,
            () -> new CloudConfig(ACCESS_KEY, "your-secret-key", "us-east-1", "lt-placeholder", "subnet-placeholder"));
        assertThrows(IllegalArgumentException.class,
            () -> new CloudConfig("TEST_ACCESS_KEY_ID", "TEST_SECRET_ACCESS_KEY", "us-east-1", "lt-placeholder", "subnet-placeholder"));
    }
}
