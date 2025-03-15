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

public class CloudManager {
    private AmazonEC2 ec2Client;
    private AmazonCloudWatch cloudWatchClient;
    private AmazonAutoScaling autoScalingClient;
    private LoadBalancer balancer;
    private String launchTemplateId;
    private String autoScalingGroupName;

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

    // Initialize cloud servers using Auto Scaling
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

    // Fetch metrics from CloudWatch and update server objects
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

    // Scale servers based on load
    public void scaleServers(int desiredCapacity) {
        UpdateAutoScalingGroupRequest request = new UpdateAutoScalingGroupRequest()
                .withAutoScalingGroupName(autoScalingGroupName)
                .withDesiredCapacity(desiredCapacity);
        autoScalingClient.updateAutoScalingGroup(request);
    }

    // Shut down cloud resources
    public void shutdown() {
        DeleteAutoScalingGroupRequest deleteRequest = new DeleteAutoScalingGroupRequest()
                .withAutoScalingGroupName(autoScalingGroupName)
                .withForceDelete(true);
        autoScalingClient.deleteAutoScalingGroup(deleteRequest);
        ec2Client.shutdown();
        cloudWatchClient.shutdown();
        autoScalingClient.shutdown();
    }
    public int getMinServers() {
		return autoScalingClient.describeAutoScalingGroups(
			new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName)
		).getAutoScalingGroups().get(0).getMinSize();
	}

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
