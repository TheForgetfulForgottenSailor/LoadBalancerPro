package core;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CloudManagerGuardrailTest {
    private static final String ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    @TempDir
    Path tempDir;

    @Test
    void dryRunWithInjectedClientsDoesNotMutateAws() throws Exception {
        AmazonEC2 ec2 = mock(AmazonEC2.class);
        AmazonCloudWatch cloudWatch = mock(AmazonCloudWatch.class);
        AmazonAutoScaling autoScaling = mock(AmazonAutoScaling.class);
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, ec2, cloudWatch, autoScaling, null);

        manager.initializeCloudServers(1, 2);
        manager.scaleServers(2);
        manager.updateServerMetricsFromCloud();
        manager.startBackgroundJobs();
        manager.shutdown();

        verify(autoScaling, never()).createAutoScalingGroup(any());
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
        verify(autoScaling, never()).deleteAutoScalingGroup(any(DeleteAutoScalingGroupRequest.class));
        verify(cloudWatch, never()).getMetricStatistics(any(GetMetricStatisticsRequest.class));
        verify(ec2, never()).createTags(any(CreateTagsRequest.class));
    }

    @Test
    void dryRunScalingCallbackDoesNotRequireAwsMutation() {
        AmazonAutoScaling autoScaling = mock(AmazonAutoScaling.class);
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(false);

        manager.scaleServersAsync(3, callbackResult::set);
        manager.shutdown();

        assertTrue(callbackResult.get(), "Dry-run scaling should complete locally.");
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveScaleAboveMaxDesiredCapacityIsDenied() throws InterruptedException {
        AmazonAutoScaling autoScaling = mock(AmazonAutoScaling.class);
        CloudConfig config = liveConfigWithMutationGuardrails(CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "2");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        scaleAndWait(manager, 3);

        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveScaleStepAboveMaxScaleStepIsDenied() throws InterruptedException {
        AmazonAutoScaling autoScaling = mock(AmazonAutoScaling.class);
        CloudConfig config = liveConfigWithMutationGuardrails(
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "1");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        scaleAndWait(manager, 3);

        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveMutationWithoutOperatorIntentIsDenied() throws InterruptedException {
        AmazonAutoScaling autoScaling = mock(AmazonAutoScaling.class);
        Properties props = new Properties();
        props.setProperty(CloudConfig.LIVE_MODE_PROPERTY, "true");
        props.setProperty(CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10");
        props.setProperty(CloudConfig.MAX_SCALE_STEP_PROPERTY, "10");
        props.setProperty("retryAttempts", "1");
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        scaleAndWait(manager, 1);

        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void auditLogsLiveScaleDeniedWhenLiveMutationIsDisabled() throws Exception {
        String auditLog = auditLogForScale(1,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "false",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10");

        assertTrue(auditLog.contains("AUDIT cloud.scale.decision"));
        assertTrue(auditLog.contains("decision=DENY"));
        assertTrue(auditLog.contains("reason=ALLOW_LIVE_MUTATION_DISABLED"));
    }

    @Test
    void auditLogsLiveScaleDeniedWhenOperatorIntentIsInvalid() throws Exception {
        String auditLog = auditLogForScale(1,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "wrong-intent",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10");

        assertTrue(auditLog.contains("AUDIT cloud.scale.decision"));
        assertTrue(auditLog.contains("decision=DENY"));
        assertTrue(auditLog.contains("reason=OPERATOR_INTENT_INVALID"));
    }

    @Test
    void auditLogsLiveScaleDeniedWhenDesiredCapacityExceedsMax() throws Exception {
        String auditLog = auditLogForScale(3,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "2",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10");

        assertTrue(auditLog.contains("AUDIT cloud.scale.decision"));
        assertTrue(auditLog.contains("decision=DENY"));
        assertTrue(auditLog.contains("desiredCapacity=3"));
        assertTrue(auditLog.contains("maxDesiredCapacity=2"));
        assertTrue(auditLog.contains("reason=MAX_DESIRED_CAPACITY_EXCEEDED"));
    }

    @Test
    void auditLogsLiveScaleDeniedWhenScaleStepExceedsMax() throws Exception {
        String auditLog = auditLogForScale(3,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "1");

        assertTrue(auditLog.contains("AUDIT cloud.scale.decision"));
        assertTrue(auditLog.contains("decision=DENY"));
        assertTrue(auditLog.contains("scaleStep=3"));
        assertTrue(auditLog.contains("maxScaleStep=1"));
        assertTrue(auditLog.contains("reason=MAX_SCALE_STEP_EXCEEDED"));
    }

    @Test
    void auditLogsLiveScaleAllowedWithinGuardrails() throws Exception {
        String auditLog = auditLogForScale(2,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "2");

        assertTrue(auditLog.contains("AUDIT cloud.scale.decision"));
        assertTrue(auditLog.contains("decision=ALLOW"));
        assertTrue(auditLog.contains("desiredCapacity=2"));
        assertTrue(auditLog.contains("scaleStep=2"));
        assertTrue(auditLog.contains("reason=GUARDRAILS_PASSED"));
    }

    @Test
    void auditLogDoesNotExposeAwsCredentials() throws Exception {
        String auditLog = auditLogForScale(1,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "false",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10");

        assertFalse(auditLog.contains(ACCESS_KEY));
        assertFalse(auditLog.contains(SECRET_KEY));
    }

    @Test
    void deletionRequiresLiveModeOwnershipAndDeletionApproval() {
        assertDeletionSkipped(configWithDeletionFlags(false, true, true));
        assertDeletionSkipped(configWithDeletionFlags(true, false, true));
        assertDeletionSkipped(configWithDeletionFlags(true, true, false));
    }

    @Test
    void deletionRunsOnlyWhenAllDeletionGatesAreExplicitlyEnabled() {
        AmazonAutoScaling autoScaling = mock(AmazonAutoScaling.class);
        CloudManager manager = new CloudManager(
                new LoadBalancer(),
                configWithDeletionFlags(true, true, true),
                null,
                null,
                autoScaling,
                null);

        manager.shutdown();

        verify(autoScaling).deleteAutoScalingGroup(any(DeleteAutoScalingGroupRequest.class));
    }

    private static void assertDeletionSkipped(CloudConfig config) {
        AmazonAutoScaling autoScaling = mock(AmazonAutoScaling.class);
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        manager.shutdown();

        verify(autoScaling, never()).deleteAutoScalingGroup(any(DeleteAutoScalingGroupRequest.class));
    }

    private static CloudConfig configWithDeletionFlags(boolean liveMode, boolean ownershipConfirmed,
                                                      boolean deletionAllowed) {
        Properties props = new Properties();
        props.setProperty(CloudConfig.LIVE_MODE_PROPERTY, Boolean.toString(liveMode));
        props.setProperty(CloudConfig.CONFIRM_RESOURCE_OWNERSHIP_PROPERTY, Boolean.toString(ownershipConfirmed));
        props.setProperty(CloudConfig.ALLOW_RESOURCE_DELETION_PROPERTY, Boolean.toString(deletionAllowed));
        props.setProperty("retryAttempts", "1");
        return new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);
    }

    private static CloudConfig liveConfigWithMutationGuardrails(String... keyValues) {
        Properties props = new Properties();
        props.setProperty(CloudConfig.LIVE_MODE_PROPERTY, "true");
        props.setProperty(CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true");
        props.setProperty(CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION");
        props.setProperty("retryAttempts", "1");
        for (int i = 0; i < keyValues.length; i += 2) {
            props.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);
    }

    private String auditLogForScale(int desiredCapacity, String... keyValues)
            throws InterruptedException, IOException {
        Path logFile = tempDir.resolve("cloud-manager-" + System.nanoTime() + ".log");
        Properties props = new Properties();
        props.setProperty(CloudConfig.LIVE_MODE_PROPERTY, "true");
        props.setProperty("retryAttempts", "1");
        props.setProperty("logFile", logFile.toString());
        for (int i = 0; i < keyValues.length; i += 2) {
            props.setProperty(keyValues[i], keyValues[i + 1]);
        }
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, mock(AmazonAutoScaling.class), null);

        scaleAndWait(manager, desiredCapacity);

        return Files.readString(logFile);
    }

    private static void scaleAndWait(CloudManager manager, int desiredCapacity) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        manager.scaleServersAsync(desiredCapacity, ignored -> latch.countDown());

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Scale callback should complete during the test.");
        manager.shutdown();
    }
}
