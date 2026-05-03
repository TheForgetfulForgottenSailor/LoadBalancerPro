package core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.CreateAutoScalingGroupRequest;
import software.amazon.awssdk.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse;
import software.amazon.awssdk.services.autoscaling.model.TagDescription;
import software.amazon.awssdk.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.Reservation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.when;

class CloudManagerGuardrailTest {
    private static final String ACCESS_KEY = "UNIT_TEST_ACCESS_KEY_ID";
    private static final String SECRET_KEY = "UNIT_TEST_SECRET_ACCESS_KEY";
    private static final String CLOUD_ENVIRONMENT_PROPERTY = "cloud.environment";
    private static final String CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY = "cloud.allowedAwsAccountIds";
    private static final String CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY = "cloud.currentAwsAccountId";
    private static final String CLOUD_ALLOWED_REGIONS_PROPERTY = "cloud.allowedRegions";
    private static final String ALLOWED_ACCOUNT_ID = "123456789012";
    private static final String DISALLOWED_ACCOUNT_ID = "999999999999";
    private static final String DEPLOY_ENVIRONMENT = "prod";
    @TempDir
    Path tempDir;

    @Test
    void dryRunWithInjectedClientsDoesNotMutateAws() throws Exception {
        Ec2Client ec2 = mock(Ec2Client.class);
        CloudWatchClient cloudWatch = mock(CloudWatchClient.class);
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, ec2, cloudWatch, autoScaling, null);

        manager.initializeCloudServers(1, 2);
        manager.scaleServers(2);
        manager.updateServerMetricsFromCloud();
        manager.startBackgroundJobs();
        manager.shutdown();

        verify(autoScaling, never()).createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class));
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
        verify(autoScaling, never()).deleteAutoScalingGroup(any(DeleteAutoScalingGroupRequest.class));
        verify(cloudWatch, never()).getMetricStatistics(any(GetMetricStatisticsRequest.class));
        verify(ec2, never()).createTags(any(CreateTagsRequest.class));
    }

    @Test
    void dryRunScalingCallbackDoesNotRequireAwsMutation() {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
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
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithMutationGuardrails(CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "2");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        scaleAndWait(manager, 3);

        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveScaleStepAboveMaxScaleStepIsDenied() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithMutationGuardrails(
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "1");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        scaleAndWait(manager, 3);

        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveMutationWithoutOperatorIntentIsDenied() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
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
    void liveAsgCreationWithoutMutationGuardrailsIsDenied() throws InterruptedException {
        Ec2Client ec2 = mock(Ec2Client.class);
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        Properties props = new Properties();
        props.setProperty(CloudConfig.LIVE_MODE_PROPERTY, "true");
        props.setProperty(CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10");
        props.setProperty(CloudConfig.MAX_SCALE_STEP_PROPERTY, "10");
        props.setProperty("retryAttempts", "1");
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResultWithInstances(config, "i-owned-1"));
        when(ec2.describeInstances()).thenReturn(DescribeInstancesResponse.builder().build());
        CloudManager manager = new CloudManager(new LoadBalancer(), config, ec2, null, autoScaling, null);

        manager.initializeCloudServers(1, 2);
        manager.shutdown();

        verify(autoScaling, never()).createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class));
    }

    @Test
    void asgCreationFailureStopsInitializationBeforeWaitRegisterOrTag() throws InterruptedException {
        Ec2Client ec2 = mock(Ec2Client.class);
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithMutationGuardrails(
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, DEPLOY_ENVIRONMENT,
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        when(autoScaling.createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class)))
                .thenThrow(SdkServiceException.builder().message("create failed").build());
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResultWithInstances(config, "i-owned-1"));
        when(ec2.describeInstances()).thenReturn(DescribeInstancesResponse.builder()
                .reservations(Reservation.builder().instances(runningEc2Instance("i-owned-1")).build())
                .build());
        CloudManager manager = new CloudManager(new LoadBalancer(), config, ec2, null, autoScaling, null);

        manager.initializeCloudServers(1, 2);

        verify(autoScaling).createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class));
        verify(autoScaling, after(500).never()).describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class));
        verify(ec2, never()).describeInstances();
        verify(ec2, never()).createTags(any(CreateTagsRequest.class));
        manager.shutdown();
    }

    @Test
    void guardrailDeniedInitializationDoesNotStartBackgroundJobs() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        Properties props = new Properties();
        props.setProperty(CloudConfig.LIVE_MODE_PROPERTY, "true");
        props.setProperty(CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10");
        props.setProperty(CloudConfig.MAX_SCALE_STEP_PROPERTY, "10");
        props.setProperty("retryAttempts", "1");
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        manager.initializeCloudServers(1, 2);

        verify(autoScaling, never()).createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class));
        verify(autoScaling, after(500).never()).describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class));
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
        manager.shutdown();
    }

    @Test
    void liveScaleDescribeFailureDoesNotUpdateAutoScalingGroup() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithMutationGuardrails(
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, DEPLOY_ENVIRONMENT,
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenThrow(SdkServiceException.builder().message("describe failed").build());
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(true);

        scaleAndWait(manager, 1, callbackResult::set);

        assertFalse(callbackResult.get(), "Scaling must fail closed when current ASG capacity cannot be described.");
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void initializationRegistersOnlyInstancesOwnedByAutoScalingGroup() throws InterruptedException {
        Ec2Client ec2 = mock(Ec2Client.class);
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithMutationGuardrails(
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, DEPLOY_ENVIRONMENT,
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        LoadBalancer balancer = new LoadBalancer();
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResultWithInstances(config, "i-owned-1"));
        when(ec2.describeInstances()).thenReturn(DescribeInstancesResponse.builder()
                .reservations(Reservation.builder().instances(
                        runningEc2Instance("i-owned-1"),
                        runningEc2Instance("i-stray-1")).build())
                .build());
        CloudManager manager = new CloudManager(balancer, config, ec2, null, autoScaling, null);

        manager.initializeCloudServers(1, 2);
        manager.shutdown();

        assertTrue(balancer.getServerMap().containsKey("i-owned-1"),
                "ASG-owned running instances should be registered.");
        assertFalse(balancer.getServerMap().containsKey("i-stray-1"),
                "Running instances outside the ASG must not be registered.");
        verify(ec2, never()).createTags(argThat((CreateTagsRequest request) ->
                request.resources() != null && request.resources().contains("i-stray-1")));
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
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "2",
                CLOUD_ENVIRONMENT_PROPERTY, DEPLOY_ENVIRONMENT,
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");

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
    void predictiveScaleUpSourceDoesNotLiveScaleWhenAutonomousScaleUpIsDisabled() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithMutationGuardrails(
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(true);

        scaleAndWait(manager, 2, CloudMutationSource.PREDICTIVE, callbackResult::set);

        assertFalse(callbackResult.get());
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void preemptivePoolingDoesNotLiveScaleWhenAutonomousScaleUpIsDisabled() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithMutationGuardrails(
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        manager.startBackgroundJobs();
        verify(autoScaling, after(500).never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
        manager.shutdown();
    }

    @Test
    void publicScaleServersAsyncApiRemainsOperatorCompatible() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithMutationGuardrails(
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, DEPLOY_ENVIRONMENT,
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResultWithDesiredCapacity(config, 0));
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(false);

        scaleAndWait(manager, 2, callbackResult::set);

        assertTrue(callbackResult.get());
        verify(autoScaling).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveScaleWithoutEnvironmentIsDenied() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithAccountGuardrails(
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(true);

        scaleAndWait(manager, 1, callbackResult::set);

        assertFalse(callbackResult.get(), "Live scale must require an explicit deployment environment.");
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveSandboxScaleWithoutOperatorIntentIsDenied() throws Exception {
        String auditLog = auditLogForScale(1,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, "sandbox",
                CloudConfig.RESOURCE_NAME_PREFIX_PROPERTY, "lbp-sandbox-",
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");

        assertTrue(auditLog.contains("decision=DENY"));
        assertTrue(auditLog.contains("reason=OPERATOR_INTENT_INVALID"));
    }

    @Test
    void liveSandboxScaleWithoutAllowedAccountListIsDenied() throws Exception {
        String auditLog = auditLogForScale(1,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, "sandbox",
                CloudConfig.RESOURCE_NAME_PREFIX_PROPERTY, "lbp-sandbox-",
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");

        assertTrue(auditLog.contains("decision=DENY"));
        assertTrue(auditLog.contains("reason=ALLOWED_ACCOUNT_LIST_MISSING"));
    }

    @Test
    void liveSandboxScaleWithDisallowedRegionIsDenied() throws Exception {
        String auditLog = auditLogForScale(1,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, "sandbox",
                CloudConfig.RESOURCE_NAME_PREFIX_PROPERTY, "lbp-sandbox-",
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-west-2");

        assertTrue(auditLog.contains("decision=DENY"));
        assertTrue(auditLog.contains("reason=REGION_NOT_ALLOWED"));
    }

    @Test
    void liveSandboxScaleWithoutResourcePrefixIsDenied() throws Exception {
        String auditLog = auditLogForScale(1,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, "sandbox",
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");

        assertTrue(auditLog.contains("decision=DENY"));
        assertTrue(auditLog.contains("reason=SANDBOX_RESOURCE_PREFIX_MISSING"));
    }

    @Test
    void liveSandboxScaleWithoutResourcePrefixDoesNotUpdateAutoScalingGroup() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithAccountGuardrails(
                CLOUD_ENVIRONMENT_PROPERTY, "sandbox",
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResultWithDesiredCapacity(config, 0));
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(true);

        scaleAndWait(manager, 1, callbackResult::set);

        assertFalse(callbackResult.get(), "Sandbox live scale must fail closed without a resource prefix.");
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveSandboxScaleWithIncorrectResourcePrefixDoesNotUpdateAutoScalingGroup() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithAccountGuardrails(
                CLOUD_ENVIRONMENT_PROPERTY, "sandbox",
                CloudConfig.RESOURCE_NAME_PREFIX_PROPERTY, "prod-",
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResultWithDesiredCapacity(config, 0));
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(true);

        scaleAndWait(manager, 1, callbackResult::set);

        assertFalse(callbackResult.get(), "Sandbox live scale must require the documented sandbox resource prefix.");
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveSandboxScaleWithCorrectResourcePrefixUpdatesOnlyPrefixedAutoScalingGroup()
            throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithAccountGuardrails(
                CLOUD_ENVIRONMENT_PROPERTY, "sandbox",
                CloudConfig.RESOURCE_NAME_PREFIX_PROPERTY, "lbp-sandbox-",
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResultWithDesiredCapacity(config, 0));
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(false);

        scaleAndWait(manager, 1, callbackResult::set);

        assertTrue(callbackResult.get(), "Correct sandbox prefix should pass existing live mutation guardrails.");
        verify(autoScaling).updateAutoScalingGroup(argThat((UpdateAutoScalingGroupRequest request) ->
                request.desiredCapacity() == 1
                        && request.autoScalingGroupName().startsWith("lbp-sandbox-LoadBalancerPro-ASG-")));
    }

    @Test
    void liveSandboxScaleWithPrefixAccountRegionAndIntentPassesExistingGuardrails() throws Exception {
        String auditLog = auditLogForScale(1,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, "sandbox",
                CloudConfig.RESOURCE_NAME_PREFIX_PROPERTY, "lbp-sandbox-",
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");

        assertTrue(auditLog.contains("decision=ALLOW"));
        assertTrue(auditLog.contains("reason=GUARDRAILS_PASSED"));
        assertTrue(auditLog.contains("environment=sandbox"));
        assertTrue(auditLog.contains("asg=lbp-sandbox-LoadBalancerPro-ASG-"));
    }

    @Test
    void liveScaleWithoutAllowedAccountListIsDenied() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithAccountGuardrails(
                CLOUD_ENVIRONMENT_PROPERTY, DEPLOY_ENVIRONMENT,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(true);

        scaleAndWait(manager, 1, callbackResult::set);

        assertFalse(callbackResult.get(), "Live scale must require an explicit AWS account allow-list.");
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveScaleWithDisallowedAccountIsDenied() throws InterruptedException {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = liveConfigWithAccountGuardrails(
                CLOUD_ENVIRONMENT_PROPERTY, DEPLOY_ENVIRONMENT,
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, DISALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);
        AtomicBoolean callbackResult = new AtomicBoolean(true);

        scaleAndWait(manager, 1, callbackResult::set);

        assertFalse(callbackResult.get(), "Live scale must deny accounts outside the allow-list.");
        verify(autoScaling, never()).updateAutoScalingGroup(any(UpdateAutoScalingGroupRequest.class));
    }

    @Test
    void liveScaleWithAllowedAccountAndRegionPassesExistingGuardrails() throws Exception {
        String auditLog = auditLogForScale(1,
                CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true",
                CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION",
                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10",
                CLOUD_ENVIRONMENT_PROPERTY, DEPLOY_ENVIRONMENT,
                CLOUD_ALLOWED_AWS_ACCOUNT_IDS_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_CURRENT_AWS_ACCOUNT_ID_PROPERTY, ALLOWED_ACCOUNT_ID,
                CLOUD_ALLOWED_REGIONS_PROPERTY, "us-east-1");

        assertTrue(auditLog.contains("decision=ALLOW"));
        assertTrue(auditLog.contains("environment=" + DEPLOY_ENVIRONMENT));
        assertTrue(auditLog.contains("accountId=" + ALLOWED_ACCOUNT_ID));
        assertTrue(auditLog.contains("region=us-east-1"));
        assertTrue(auditLog.contains("reason=GUARDRAILS_PASSED"));
    }

    @Test
    void deletionRequiresLiveModeOwnershipAndDeletionApproval() {
        assertDeletionSkipped(configWithDeletionFlags(false, true, true));
        assertDeletionSkipped(configWithDeletionFlags(true, false, true));
        assertDeletionSkipped(configWithDeletionFlags(true, true, false));
    }

    @Test
    void deletionRunsOnlyWhenAllDeletionGatesAreExplicitlyEnabled() {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = configWithDeletionFlags(true, true, true);
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResult(config, config.getAutoScalingGroupName()));
        CloudManager manager = new CloudManager(
                new LoadBalancer(),
                config,
                null,
                null,
                autoScaling,
                null);

        manager.shutdown();

        verify(autoScaling).deleteAutoScalingGroup(any(DeleteAutoScalingGroupRequest.class));
    }

    @Test
    void deletionWithoutMatchingOwnershipTagDoesNotDelete() {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = configWithDeletionFlags(true, true, true);
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResult(config, "another-owner"));
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        manager.shutdown();

        verify(autoScaling).describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class));
        verify(autoScaling, never()).deleteAutoScalingGroup(any(DeleteAutoScalingGroupRequest.class));
    }

    @Test
    void deletionWithMatchingOwnershipTagAndAllGatesDeletes() {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = configWithDeletionFlags(true, true, true);
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResult(config, config.getAutoScalingGroupName()));
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        manager.shutdown();

        verify(autoScaling).describeAutoScalingGroups(argThat((DescribeAutoScalingGroupsRequest request) ->
                request.autoScalingGroupNames().contains(config.getAutoScalingGroupName())));
        verify(autoScaling).deleteAutoScalingGroup(any(DeleteAutoScalingGroupRequest.class));
    }

    @Test
    void deletionDescribeFailureDoesNotDelete() {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        CloudConfig config = configWithDeletionFlags(true, true, true);
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenThrow(SdkServiceException.builder().message("describe failed").build());
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        manager.shutdown();

        verify(autoScaling).describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class));
        verify(autoScaling, never()).deleteAutoScalingGroup(any(DeleteAutoScalingGroupRequest.class));
    }

    private static void assertDeletionSkipped(CloudConfig config) {
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
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

    private static DescribeAutoScalingGroupsResponse asgDescribeResult(CloudConfig config, String ownerValue) {
        AutoScalingGroup asg = AutoScalingGroup.builder()
                .autoScalingGroupName(config.getAutoScalingGroupName())
                .tags(TagDescription.builder()
                        .key("LoadBalancerPro")
                        .value(ownerValue)
                        .build())
                .build();
        return DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asg).build();
    }

    private static DescribeAutoScalingGroupsResponse asgDescribeResultWithInstances(CloudConfig config,
                                                                                   String... instanceIds) {
        AutoScalingGroup asg = AutoScalingGroup.builder()
                .autoScalingGroupName(config.getAutoScalingGroupName())
                .desiredCapacity(instanceIds.length)
                .minSize(instanceIds.length)
                .tags(TagDescription.builder()
                        .key("LoadBalancerPro")
                        .value(config.getAutoScalingGroupName())
                        .build())
                .instances(java.util.Arrays.stream(instanceIds)
                        .map(instanceId -> software.amazon.awssdk.services.autoscaling.model.Instance.builder()
                                .instanceId(instanceId)
                                .lifecycleState("InService")
                                .healthStatus("Healthy")
                                .build())
                        .toList())
                .build();
        return DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asg).build();
    }

    private static DescribeAutoScalingGroupsResponse asgDescribeResultWithDesiredCapacity(CloudConfig config,
                                                                                        int desiredCapacity) {
        AutoScalingGroup asg = AutoScalingGroup.builder()
                .autoScalingGroupName(config.getAutoScalingGroupName())
                .desiredCapacity(desiredCapacity)
                .tags(TagDescription.builder()
                        .key("LoadBalancerPro")
                        .value(config.getAutoScalingGroupName())
                        .build())
                .build();
        return DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asg).build();
    }

    private static software.amazon.awssdk.services.ec2.model.Instance runningEc2Instance(String instanceId) {
        return software.amazon.awssdk.services.ec2.model.Instance.builder()
                .instanceId(instanceId)
                .state(InstanceState.builder().name(InstanceStateName.RUNNING).build())
                .build();
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

    private static CloudConfig liveConfigWithAccountGuardrails(String... keyValues) {
        return liveConfigWithMutationGuardrails(
                combineKeyValues(
                        new String[] {
                                CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "10",
                                CloudConfig.MAX_SCALE_STEP_PROPERTY, "10"
                        },
                        keyValues));
    }

    private static String[] combineKeyValues(String[] baseKeyValues, String[] extraKeyValues) {
        String[] combined = new String[baseKeyValues.length + extraKeyValues.length];
        System.arraycopy(baseKeyValues, 0, combined, 0, baseKeyValues.length);
        System.arraycopy(extraKeyValues, 0, combined, baseKeyValues.length, extraKeyValues.length);
        return combined;
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
        AutoScalingClient autoScaling = mock(AutoScalingClient.class);
        when(autoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(asgDescribeResultWithDesiredCapacity(config, 0));
        CloudManager manager = new CloudManager(new LoadBalancer(), config, null, null, autoScaling, null);

        scaleAndWait(manager, desiredCapacity);

        return Files.readString(logFile);
    }

    private static void scaleAndWait(CloudManager manager, int desiredCapacity) throws InterruptedException {
        scaleAndWait(manager, desiredCapacity, ignored -> {});
    }

    private static void scaleAndWait(CloudManager manager, int desiredCapacity, Consumer<Boolean> callback)
            throws InterruptedException {
        scaleAndWait(manager, desiredCapacity, null, callback);
    }

    private static void scaleAndWait(CloudManager manager, int desiredCapacity, CloudMutationSource source,
                                     Consumer<Boolean> callback) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Consumer<Boolean> callbackWithLatch = success -> {
            callback.accept(success);
            latch.countDown();
        };
        if (source == null) {
            manager.scaleServersAsync(desiredCapacity, callbackWithLatch);
        } else {
            manager.scaleServersAsync(desiredCapacity, source, callbackWithLatch);
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Scale callback should complete during the test.");
        manager.shutdown();
    }
}
