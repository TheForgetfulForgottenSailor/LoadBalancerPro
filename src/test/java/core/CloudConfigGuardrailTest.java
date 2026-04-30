package core;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudConfigGuardrailTest {
    private static final String ACCESS_KEY = "UNIT_TEST_ACCESS_KEY_ID";
    private static final String SECRET_KEY = "UNIT_TEST_SECRET_ACCESS_KEY";

    @Test
    void guardrailDefaultsAreFailClosed() {
        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test");

        assertEquals(CloudConfig.DEFAULT_MAX_DESIRED_CAPACITY, config.getMaxDesiredCapacity());
        assertEquals(CloudConfig.DEFAULT_MAX_SCALE_STEP, config.getMaxScaleStep());
        assertFalse(config.isLiveMutationAllowed());
        assertEquals(CloudConfig.DEFAULT_OPERATOR_INTENT, config.getOperatorIntent());
        assertFalse(config.isAutonomousScaleUpAllowed());
        assertEquals(CloudConfig.DEFAULT_ENVIRONMENT, config.getEnvironment());
        assertEquals(CloudConfig.DEFAULT_CURRENT_AWS_ACCOUNT_ID, config.getCurrentAwsAccountId());
        assertTrue(config.getAllowedAwsAccountIds().isEmpty());
        assertTrue(config.getAllowedRegions().isEmpty());
    }

    @Test
    void validGuardrailPropertiesAreParsed() {
        Properties props = new Properties();
        props.setProperty(CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "8");
        props.setProperty(CloudConfig.MAX_SCALE_STEP_PROPERTY, "2");
        props.setProperty(CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "true");
        props.setProperty(CloudConfig.OPERATOR_INTENT_PROPERTY, "LOADBALANCERPRO_LIVE_MUTATION");
        props.setProperty(CloudConfig.ALLOW_AUTONOMOUS_SCALE_UP_PROPERTY, "true");

        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);

        assertEquals(8, config.getMaxDesiredCapacity());
        assertEquals(2, config.getMaxScaleStep());
        assertTrue(config.isLiveMutationAllowed());
        assertEquals("LOADBALANCERPRO_LIVE_MUTATION", config.getOperatorIntent());
        assertTrue(config.isAutonomousScaleUpAllowed());
    }

    @Test
    void accountAndEnvironmentAllowListPropertiesAreParsedAndTrimmed() {
        Properties props = new Properties();
        props.setProperty(CloudConfig.ENVIRONMENT_PROPERTY, " prod ");
        props.setProperty(CloudConfig.CURRENT_AWS_ACCOUNT_ID_PROPERTY, "123456789012");
        props.setProperty(CloudConfig.ALLOWED_AWS_ACCOUNT_IDS_PROPERTY,
                "123456789012, 210987654321,123456789012");
        props.setProperty(CloudConfig.ALLOWED_REGIONS_PROPERTY, "us-east-1, us-west-2,us-east-1");

        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);

        assertEquals("prod", config.getEnvironment());
        assertEquals("123456789012", config.getCurrentAwsAccountId());
        assertEquals(
                java.util.List.of("123456789012", "210987654321"),
                config.getAllowedAwsAccountIds());
        assertEquals(java.util.List.of("us-east-1", "us-west-2"), config.getAllowedRegions());
    }

    @Test
    void invalidAndBlankGuardrailValuesFailSafely() {
        Properties props = new Properties();
        props.setProperty(CloudConfig.MAX_DESIRED_CAPACITY_PROPERTY, "-1");
        props.setProperty(CloudConfig.MAX_SCALE_STEP_PROPERTY, "not-a-number");
        props.setProperty(CloudConfig.ALLOW_LIVE_MUTATION_PROPERTY, "not-true");
        props.setProperty(CloudConfig.OPERATOR_INTENT_PROPERTY, "   ");
        props.setProperty(CloudConfig.ALLOW_AUTONOMOUS_SCALE_UP_PROPERTY, "not-true");

        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);

        assertEquals(CloudConfig.DEFAULT_MAX_DESIRED_CAPACITY, config.getMaxDesiredCapacity());
        assertEquals(CloudConfig.DEFAULT_MAX_SCALE_STEP, config.getMaxScaleStep());
        assertFalse(config.isLiveMutationAllowed());
        assertEquals(CloudConfig.DEFAULT_OPERATOR_INTENT, config.getOperatorIntent());
        assertFalse(config.isAutonomousScaleUpAllowed());
    }

    @Test
    void blankAndInvalidAllowListValuesAreIgnoredSafely() {
        Properties props = new Properties();
        props.setProperty(CloudConfig.ENVIRONMENT_PROPERTY, "   ");
        props.setProperty(CloudConfig.CURRENT_AWS_ACCOUNT_ID_PROPERTY, "not-an-account");
        props.setProperty(CloudConfig.ALLOWED_AWS_ACCOUNT_IDS_PROPERTY,
                " , not-an-account, 123, 123456789012, ");
        props.setProperty(CloudConfig.ALLOWED_REGIONS_PROPERTY,
                " , bad-region, us-east-1, us_east_2, ");

        CloudConfig config = new CloudConfig(ACCESS_KEY, SECRET_KEY, "us-east-1", "lt-test", "subnet-test", props);

        assertEquals(CloudConfig.DEFAULT_ENVIRONMENT, config.getEnvironment());
        assertEquals(CloudConfig.DEFAULT_CURRENT_AWS_ACCOUNT_ID, config.getCurrentAwsAccountId());
        assertEquals(java.util.List.of("123456789012"), config.getAllowedAwsAccountIds());
        assertEquals(java.util.List.of("us-east-1"), config.getAllowedRegions());
    }
}
