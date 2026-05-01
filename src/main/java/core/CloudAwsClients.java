package core;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;

import java.util.Objects;

final class CloudAwsClients {
    private static final ClientBuilder SDK_V1_CLIENT_BUILDER = new SdkV1ClientBuilder();

    private final AmazonEC2 ec2;
    private final AmazonCloudWatch cloudWatch;
    private final AmazonAutoScaling autoScaling;

    private CloudAwsClients(AmazonEC2 ec2, AmazonCloudWatch cloudWatch, AmazonAutoScaling autoScaling) {
        this.ec2 = ec2;
        this.cloudWatch = cloudWatch;
        this.autoScaling = autoScaling;
    }

    static CloudAwsClients fromConfig(CloudConfig config) {
        return fromConfig(config, SDK_V1_CLIENT_BUILDER);
    }

    static CloudAwsClients fromConfig(CloudConfig config, ClientBuilder builder) {
        Objects.requireNonNull(config, "Config cannot be null");
        Objects.requireNonNull(builder, "Client builder cannot be null");
        if (!config.isLiveMode()) {
            return dryRun();
        }

        BasicAWSCredentials credentials = new BasicAWSCredentials(config.getAccessKey(), config.getSecretKey());
        AWSStaticCredentialsProvider provider = new AWSStaticCredentialsProvider(credentials);
        return new CloudAwsClients(
                builder.buildEc2(config, provider),
                builder.buildCloudWatch(config, provider),
                builder.buildAutoScaling(config, provider));
    }

    static CloudAwsClients dryRun() {
        return new CloudAwsClients(null, null, null);
    }

    static CloudAwsClients of(AmazonEC2 ec2, AmazonCloudWatch cloudWatch, AmazonAutoScaling autoScaling) {
        return new CloudAwsClients(ec2, cloudWatch, autoScaling);
    }

    AmazonEC2 ec2() {
        return ec2;
    }

    AmazonCloudWatch cloudWatch() {
        return cloudWatch;
    }

    AmazonAutoScaling autoScaling() {
        return autoScaling;
    }

    boolean hasLiveClients() {
        return ec2 != null || cloudWatch != null || autoScaling != null;
    }

    void shutdown() {
        if (ec2 != null) {
            ec2.shutdown();
        }
        if (cloudWatch != null) {
            cloudWatch.shutdown();
        }
        if (autoScaling != null) {
            autoScaling.shutdown();
        }
    }

    interface ClientBuilder {
        AmazonEC2 buildEc2(CloudConfig config, AWSStaticCredentialsProvider provider);

        AmazonCloudWatch buildCloudWatch(CloudConfig config, AWSStaticCredentialsProvider provider);

        AmazonAutoScaling buildAutoScaling(CloudConfig config, AWSStaticCredentialsProvider provider);
    }

    private static final class SdkV1ClientBuilder implements ClientBuilder {
        @Override
        public AmazonEC2 buildEc2(CloudConfig config, AWSStaticCredentialsProvider provider) {
            return AmazonEC2ClientBuilder.standard()
                    .withCredentials(provider)
                    .withRegion(config.getRegion())
                    .build();
        }

        @Override
        public AmazonCloudWatch buildCloudWatch(CloudConfig config, AWSStaticCredentialsProvider provider) {
            return AmazonCloudWatchClientBuilder.standard()
                    .withCredentials(provider)
                    .withRegion(config.getRegion())
                    .build();
        }

        @Override
        public AmazonAutoScaling buildAutoScaling(CloudConfig config, AWSStaticCredentialsProvider provider) {
            return AmazonAutoScalingClientBuilder.standard()
                    .withCredentials(provider)
                    .withRegion(config.getRegion())
                    .build();
        }
    }
}
