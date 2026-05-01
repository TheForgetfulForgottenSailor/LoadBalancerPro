package core;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.ec2.AmazonEC2;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CloudAwsClientsTest {
    private static final String ACCESS_KEY = "UNIT_TEST_ACCESS_KEY_ID";
    private static final String SECRET_KEY = "UNIT_TEST_SECRET_ACCESS_KEY";

    @Test
    void dryRunConfigDoesNotBuildAwsClients() {
        CountingBuilder builder = new CountingBuilder();
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test");

        CloudAwsClients clients = CloudAwsClients.fromConfig(config, builder);

        assertFalse(clients.hasLiveClients());
        assertFalse(builder.wasInvoked(), "Dry-run config must not construct AWS SDK clients.");
    }

    @Test
    void liveModeConfigBuildsAwsClientsThroughSeam() {
        CountingBuilder builder = new CountingBuilder();
        Properties props = new Properties();
        props.setProperty(CloudConfig.LIVE_MODE_PROPERTY, "true");
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);

        CloudAwsClients clients = CloudAwsClients.fromConfig(config, builder);

        assertTrue(clients.hasLiveClients());
        assertSame(builder.ec2, clients.ec2());
        assertSame(builder.cloudWatch, clients.cloudWatch());
        assertSame(builder.autoScaling, clients.autoScaling());
        assertTrue(builder.wasInvoked(), "Live config must build AWS clients through the seam.");
    }

    private static final class CountingBuilder implements CloudAwsClients.ClientBuilder {
        private final AmazonEC2 ec2 = mock(AmazonEC2.class);
        private final AmazonCloudWatch cloudWatch = mock(AmazonCloudWatch.class);
        private final AmazonAutoScaling autoScaling = mock(AmazonAutoScaling.class);
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public AmazonEC2 buildEc2(CloudConfig config, AWSStaticCredentialsProvider provider) {
            invocations.incrementAndGet();
            return ec2;
        }

        @Override
        public AmazonCloudWatch buildCloudWatch(CloudConfig config, AWSStaticCredentialsProvider provider) {
            invocations.incrementAndGet();
            return cloudWatch;
        }

        @Override
        public AmazonAutoScaling buildAutoScaling(CloudConfig config, AWSStaticCredentialsProvider provider) {
            invocations.incrementAndGet();
            return autoScaling;
        }

        private boolean wasInvoked() {
            return invocations.get() > 0;
        }
    }
}
