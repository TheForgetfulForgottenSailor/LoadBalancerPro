package core;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.ec2.Ec2Client;

import java.util.Objects;

final class CloudAwsClients {
    private static final ClientBuilder SDK_V2_CLIENT_BUILDER = new SdkV2ClientBuilder();

    private final Ec2Client ec2;
    private final CloudWatchClient cloudWatch;
    private final AutoScalingClient autoScaling;

    private CloudAwsClients(Ec2Client ec2, CloudWatchClient cloudWatch, AutoScalingClient autoScaling) {
        this.ec2 = ec2;
        this.cloudWatch = cloudWatch;
        this.autoScaling = autoScaling;
    }

    static CloudAwsClients fromConfig(CloudConfig config) {
        return fromConfig(config, SDK_V2_CLIENT_BUILDER);
    }

    static CloudAwsClients fromConfig(CloudConfig config, ClientBuilder builder) {
        Objects.requireNonNull(config, "Config cannot be null");
        Objects.requireNonNull(builder, "Client builder cannot be null");
        if (!config.isLiveMode()) {
            return dryRun();
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey());
        StaticCredentialsProvider provider = StaticCredentialsProvider.create(credentials);
        return new CloudAwsClients(
                builder.buildEc2(config, provider),
                builder.buildCloudWatch(config, provider),
                builder.buildAutoScaling(config, provider));
    }

    static CloudAwsClients dryRun() {
        return new CloudAwsClients(null, null, null);
    }

    static CloudAwsClients of(Ec2Client ec2, CloudWatchClient cloudWatch, AutoScalingClient autoScaling) {
        return new CloudAwsClients(ec2, cloudWatch, autoScaling);
    }

    Ec2Client ec2() {
        return ec2;
    }

    CloudWatchClient cloudWatch() {
        return cloudWatch;
    }

    AutoScalingClient autoScaling() {
        return autoScaling;
    }

    boolean hasLiveClients() {
        return ec2 != null || cloudWatch != null || autoScaling != null;
    }

    void shutdown() {
        if (ec2 != null) {
            ec2.close();
        }
        if (cloudWatch != null) {
            cloudWatch.close();
        }
        if (autoScaling != null) {
            autoScaling.close();
        }
    }

    interface ClientBuilder {
        Ec2Client buildEc2(CloudConfig config, StaticCredentialsProvider provider);

        CloudWatchClient buildCloudWatch(CloudConfig config, StaticCredentialsProvider provider);

        AutoScalingClient buildAutoScaling(CloudConfig config, StaticCredentialsProvider provider);
    }

    private static final class SdkV2ClientBuilder implements ClientBuilder {
        @Override
        public Ec2Client buildEc2(CloudConfig config, StaticCredentialsProvider provider) {
            return Ec2Client.builder()
                    .credentialsProvider(provider)
                    .region(Region.of(config.getRegion()))
                    .build();
        }

        @Override
        public CloudWatchClient buildCloudWatch(CloudConfig config, StaticCredentialsProvider provider) {
            return CloudWatchClient.builder()
                    .credentialsProvider(provider)
                    .region(Region.of(config.getRegion()))
                    .build();
        }

        @Override
        public AutoScalingClient buildAutoScaling(CloudConfig config, StaticCredentialsProvider provider) {
            return AutoScalingClient.builder()
                    .credentialsProvider(provider)
                    .region(Region.of(config.getRegion()))
                    .build();
        }
    }
}
