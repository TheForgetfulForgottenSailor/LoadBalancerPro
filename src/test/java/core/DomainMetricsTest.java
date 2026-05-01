package core;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse;
import util.Utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomainMetricsTest {
    private static final String ACCESS_KEY = "UNIT_TEST_ACCESS_KEY_ID";
    private static final String SECRET_KEY = "UNIT_TEST_SECRET_ACCESS_KEY";

    private SimpleMeterRegistry registry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
        registry.close();
    }

    @Test
    void allocationMetricsAreRecordedForCapacityAwarePlanning() {
        LoadBalancer balancer = new LoadBalancer();
        try {
            Server server = new Server("S1", 10.0, 10.0, 10.0, ServerType.ONSITE);
            server.setCapacity(25.0);
            balancer.addServer(server);

            balancer.capacityAwareWithResult(40.0);

            assertEquals(1.0, registry.counter(DomainMetrics.ALLOCATION_REQUESTS, "strategy", "CAPACITY_AWARE").count());
            assertEquals(25.0, registry.summary(
                    DomainMetrics.ALLOCATION_UNALLOCATED_LOAD, "strategy", "CAPACITY_AWARE").totalAmount());
            assertEquals(1.0, registry.summary(
                    DomainMetrics.ALLOCATION_SERVER_COUNT, "strategy", "CAPACITY_AWARE").totalAmount());
        } finally {
            balancer.shutdown();
        }
    }

    @Test
    void cloudScaleDecisionMetricsIncludeDecisionAndSourceTags() throws Exception {
        CloudConfig dryRunConfig = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test");
        CloudManager dryRunManager = new CloudManager(
                new LoadBalancer(), dryRunConfig, null, null, mock(AutoScalingClient.class), null);
        dryRunManager.scaleServersAsync(2, CloudMutationSource.PREDICTIVE, ignored -> {});
        dryRunManager.shutdown();

        assertEquals(1.0, registry.counter(
                DomainMetrics.CLOUD_SCALE_DRY_RUN, "source", "PREDICTIVE", "reason", "DRY_RUN").count());
        assertEquals(1.0, registry.counter(
                DomainMetrics.CLOUD_SCALE_SOURCE,
                "source", "PREDICTIVE",
                "decision", "DRY_RUN",
                "reason", "DRY_RUN").count());

        AutoScalingClient deniedAutoScaling = mock(AutoScalingClient.class);
        CloudManager deniedManager = new CloudManager(
                new LoadBalancer(), liveConfig(), null, null, deniedAutoScaling, null);
        scaleAndWait(deniedManager, 1, CloudMutationSource.OPERATOR);
        deniedManager.shutdown();

        assertEquals(1.0, registry.counter(
                DomainMetrics.CLOUD_SCALE_DENIED,
                "source", "OPERATOR",
                "reason", "ALLOW_LIVE_MUTATION_DISABLED").count());

        AutoScalingClient allowedAutoScaling = mock(AutoScalingClient.class);
        CloudConfig allowedConfig = liveConfigWithGuardrails();
        when(allowedAutoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(DescribeAutoScalingGroupsResponse.builder()
                        .autoScalingGroups(AutoScalingGroup.builder()
                                .autoScalingGroupName(allowedConfig.getAutoScalingGroupName())
                                .desiredCapacity(0)
                                .build())
                        .build());
        CloudManager allowedManager = new CloudManager(
                new LoadBalancer(), allowedConfig, null, null, allowedAutoScaling, null);
        scaleAndWait(allowedManager, 1, CloudMutationSource.OPERATOR);
        allowedManager.shutdown();

        assertEquals(1.0, registry.counter(
                DomainMetrics.CLOUD_SCALE_ALLOWED,
                "source", "OPERATOR",
                "reason", "GUARDRAILS_PASSED").count());
        assertEquals(1.0, registry.counter(
                DomainMetrics.CLOUD_SCALE_SOURCE,
                "source", "OPERATOR",
                "decision", "ALLOW",
                "reason", "GUARDRAILS_PASSED").count());
    }

    @Test
    void parserFailureMetricsAreRecorded() throws Exception {
        Path csv = tempDir.resolve("servers.csv");
        Files.writeString(csv, "too,few,fields\n");
        Utils.importServerLogs(csv.toString(), "csv", new LoadBalancer());

        Path json = tempDir.resolve("servers.json");
        Files.writeString(json, "{not-json");
        assertThrows(RuntimeException.class, () -> Utils.importServerLogs(json.toString(), "json", new LoadBalancer()));

        assertEquals(1.0, registry.counter(DomainMetrics.CSV_PARSE_FAILURES).count());
        assertEquals(1.0, registry.counter(DomainMetrics.JSON_PARSE_FAILURES).count());
    }

    private static CloudConfig liveConfig() {
        Properties props = new Properties();
        props.setProperty(CloudConfig.LIVE_MODE_PROPERTY, "true");
        props.setProperty("retryAttempts", "1");
        return new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);
    }

    private static CloudConfig liveConfigWithGuardrails() {
        Properties props = new Properties();
        props.setProperty(CloudConfig.LIVE_MODE_PROPERTY, "true");
        props.setProperty(CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true");
        props.setProperty(CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION");
        props.setProperty(CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "5");
        props.setProperty(CloudConfig.MAX_SCALE_STEP_PROPERTY, "5");
        props.setProperty(CloudConfig.ENVIRONMENT_PROPERTY, "prod");
        props.setProperty(CloudConfig.ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, "123456789012");
        props.setProperty(CloudConfig.CURRENT_AWS_ACCOUNT_ID_PROPERTY, "123456789012");
        props.setProperty(CloudConfig.ALLOWED_REGIONS_PROPERTY, "us-east-1");
        props.setProperty("retryAttempts", "1");
        return new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);
    }

    private static void scaleAndWait(CloudManager manager, int desiredCapacity, CloudMutationSource source)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        manager.scaleServersAsync(desiredCapacity, source, ignored -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
    }
}
