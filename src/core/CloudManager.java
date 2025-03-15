package core;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Date;

/**
 * Manages cloud infrastructure integration with AWS for the LoadBalancer.
 *
 * This class provides functionality to initialize cloud servers using AWS Auto Scaling,
 * update server metrics from AWS CloudWatch, scale servers based on load, and shut down
 * cloud resources. It integrates with the LoadBalancer to manage cloud-based servers.
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Initializes and manages AWS EC2 instances via Auto Scaling.</li>
 *   <li>Fetches and updates server metrics from AWS CloudWatch.</li>
 *   <li>Supports dynamic scaling of cloud servers.</li>
 *   <li>Handles graceful shutdown of cloud resources.</li>
 * </ul>
 *
 * <p><b>Notes:</b> The `launchTemplateId` and `VPCZoneIdentifier` are hardcoded placeholders
 * and should be replaced with actual AWS values for production use.</p>
 *
 * <p><b>UML Diagram:</b></p>
 * <p><img src="cloudmanager.png" alt="CloudManager UML Diagram"></p>
 *
 * @author Richmond Dhaenens
 * @version 80.8
 */
public class CloudManager {
    /** Amazon EC2 client for managing EC2 instances. */
    private AmazonEC2 ec2Client;

    /** Amazon CloudWatch client for retrieving server metrics. */
    private AmazonCloudWatch cloudWatchClient;

    /** Amazon AutoScaling client for managing server scaling. */
    private AmazonAutoScaling autoScalingClient;

    /** LoadBalancer instance to manage cloud servers. */
    private LoadBalancer balancer;

    /** Launch Template ID for creating EC2 instances (replace with actual ID). */
    private String launchTemplateId;

    /** Auto Scaling Group name for managing cloud server instances. */
    private String autoScalingGroupName;

    /**
     * Constructs a CloudManager with AWS credentials and region configuration.
     *
     * Initializes AWS clients (EC2, CloudWatch, AutoScaling) using the provided credentials
     * and sets up the LoadBalancer integration. The `launchTemplateId` and `autoScalingGroupName`
     * are hardcoded placeholders.
     *
     * @param balancer the LoadBalancer instance to integrate with
     * @param accessKey the AWS access key for authentication
     * @param secretKey the AWS secret key for authentication
     * @param region the AWS region (e.g., "us-east-1")
     */
    public CloudManager(LoadBalancer balancer, String accessKey, String secretKey, String region) {
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        this.ec2Client = AmazonEC2ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region)
                .build();
        this.cloudWatchClient = AmazonCloudWatchClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region)
                .build();
        this.autoScalingClient = AmazonAutoScalingClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region)
                .build();
        this.balancer = balancer;
        this.launchTemplateId = "lt-1234567890abcdef0"; // Replace with your Launch Template ID
        this.autoScalingGroupName = "LoadBalancerPro-ASG";
    }

    /**
     * Initializes cloud servers using AWS Auto Scaling.
     *
     * Creates or updates an Auto Scaling Group with the specified minimum and maximum
     * server counts, waits for instances to launch, and adds running instances to the LoadBalancer.
     * The `launchTemplateId` and `VPCZoneIdentifier` are hardcoded placeholders.
     *
     * @param minServers the minimum number of servers to maintain
     * @param maxServers the maximum number of servers to scale to
     * @throws InterruptedException if the sleep operation is interrupted
     */
    public void initializeCloudServers(int minServers, int maxServers) {
        // Create or update Auto Scaling Group
        CreateAutoScalingGroupRequest asgRequest = new CreateAutoScalingGroupRequest()
                .withAutoScalingGroupName(autoScalingGroupName)
                .withMinSize(minServers)
                .withMaxSize(maxServers)
                .withDesiredCapacity(minServers)
                .withLaunchTemplate(new com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification()
                    .withLaunchTemplateId(launchTemplateId)) 
                .withVPCZoneIdentifier("subnet-12345678"); // Replace with your subnet ID
        
        autoScalingClient.createAutoScalingGroup(asgRequest);

        // Wait for instances to launch
        try {
            TimeUnit.SECONDS.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Discover running instances
        DescribeInstancesResult result = ec2Client.describeInstances();
        for (Reservation reservation : result.getReservations()) {
            for (com.amazonaws.services.ec2.model.Instance instance : reservation.getInstances()) {
                if (instance.getState().getName().equals("running")) {
                    String instanceId = instance.getInstanceId();
                    Server server = new Server(instanceId, 10.0, 20.0, 30.0);
                    server.setCapacity(500.0);
                    balancer.addServer(server);
                }
            }
        }
    }

    /**
     * Fetches metrics from AWS CloudWatch and updates server objects in the LoadBalancer.
     *
     * Retrieves CPU utilization metrics for each server and approximates memory and disk usage.
     * Updates the server's metrics if data points are available.
     *
     * <p><b>Note:</b> Memory and disk usage are simulated (multiplied by 1.2 and 0.8 from CPU)
     * as CloudWatch requires additional setup for these metrics.</p>
     */
    public void updateServerMetricsFromCloud() {
        for (Server server : balancer.getServers()) {
            String instanceId = server.getServerId();
            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                    .withNamespace("AWS/EC2")
                    .withMetricName("CPUUtilization")
                    .withDimensions(new Dimension().withName("InstanceId").withValue(instanceId))
                    .withStartTime(new Date(System.currentTimeMillis() - 300000)) 
                    .withEndTime(new Date(System.currentTimeMillis())) 
                    .withPeriod(60)
                    .withStatistics(Statistic.Average);
            GetMetricStatisticsResult result = cloudWatchClient.getMetricStatistics(request);
            if (!result.getDatapoints().isEmpty()) {
                double cpu = result.getDatapoints().get(0).getAverage();
                // Approximate memory and disk usage (CloudWatch needs additional setup for these)
                double mem = cpu * 1.2; // Simulated
                double disk = cpu * 0.8; // Simulated
                server.updateMetrics(cpu, mem, disk);
            }
        }
    }

    /**
     * Scales the number of cloud servers to the desired capacity.
     *
     * Updates the Auto Scaling Group with the specified desired capacity using AWS AutoScaling.
     *
     * @param desiredCapacity the desired number of servers
     */
    public void scaleServers(int desiredCapacity) {
        UpdateAutoScalingGroupRequest request = new UpdateAutoScalingGroupRequest()
                .withAutoScalingGroupName(autoScalingGroupName)
                .withDesiredCapacity(desiredCapacity);
        autoScalingClient.updateAutoScalingGroup(request);
    }

    /**
     * Shuts down all cloud resources.
     *
     * Deletes the Auto Scaling Group and shuts down the EC2, CloudWatch, and AutoScaling clients.
     */
    public void shutdown() {
        DeleteAutoScalingGroupRequest deleteRequest = new DeleteAutoScalingGroupRequest()
                .withAutoScalingGroupName(autoScalingGroupName)
                .withForceDelete(true);
        autoScalingClient.deleteAutoScalingGroup(deleteRequest);
        ec2Client.shutdown();
        cloudWatchClient.shutdown();
        autoScalingClient.shutdown();
    }

    /**
     * Retrieves the minimum number of servers configured in the Auto Scaling Group.
     *
     * @return the minimum number of servers
     */
    public int getMinServers() {
        return autoScalingClient.describeAutoScalingGroups(
            new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName)
        ).getAutoScalingGroups().get(0).getMinSize();
    }

    /**
     * Retrieves a specific metric for a given instance from AWS CloudWatch.
     *
     * Queries CloudWatch for the average value of the specified metric over the last 5 minutes.
     *
     * @param instanceId the ID of the EC2 instance
     * @param metricName the name of the metric (e.g., "CPUUtilization")
     * @return the average metric value, or 0.0 if no data points are available
     */
    public double getCloudMetric(String instanceId, String metricName) {
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
            .withNamespace("AWS/EC2")
            .withMetricName(metricName)
            .withDimensions(new Dimension().withName("InstanceId").withValue(instanceId))
            .withStartTime(new java.util.Date(System.currentTimeMillis() - 300000))
            .withEndTime(new java.util.Date(System.currentTimeMillis()))
            .withPeriod(60)
            .withStatistics(Statistic.Average);
        GetMetricStatisticsResult result = cloudWatchClient.getMetricStatistics(request);
        return result.getDatapoints().isEmpty() ? 0.0 : result.getDatapoints().get(0).getAverage();
    }
}
