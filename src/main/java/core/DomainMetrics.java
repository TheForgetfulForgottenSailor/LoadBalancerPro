package core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;

import java.util.Locale;

/**
 * Centralizes domain metric names so core, cloud, and parser paths report the
 * same Micrometer series without introducing framework dependencies.
 */
public final class DomainMetrics {
    public static final String ALLOCATION_REQUESTS = "allocation.requests.count";
    public static final String ALLOCATION_UNALLOCATED_LOAD = "allocation.unallocated.load";
    public static final String ALLOCATION_SERVER_COUNT = "allocation.server.count";
    public static final String CLOUD_SCALE_ALLOWED = "cloud.scale.allowed.count";
    public static final String CLOUD_SCALE_DENIED = "cloud.scale.denied.count";
    public static final String CLOUD_SCALE_DRY_RUN = "cloud.scale.dryrun.count";
    public static final String CLOUD_SCALE_SOURCE = "cloud.scale.source";
    public static final String CSV_PARSE_FAILURES = "csv.parse.failures";
    public static final String JSON_PARSE_FAILURES = "json.parse.failures";

    private DomainMetrics() {
    }

    public static void recordAllocation(String strategy, int serverCount, double unallocatedLoad) {
        String safeStrategy = safeTag(strategy);
        Counter.builder(ALLOCATION_REQUESTS)
                .tag("strategy", safeStrategy)
                .register(Metrics.globalRegistry)
                .increment();
        DistributionSummary.builder(ALLOCATION_SERVER_COUNT)
                .tag("strategy", safeStrategy)
                .register(Metrics.globalRegistry)
                .record(Math.max(0, serverCount));
        DistributionSummary.builder(ALLOCATION_UNALLOCATED_LOAD)
                .tag("strategy", safeStrategy)
                .baseUnit("gigabytes")
                .register(Metrics.globalRegistry)
                .record(Math.max(0.0, unallocatedLoad));
    }

    public static void recordCloudScaleDecision(String decision, CloudMutationSource source, String reason) {
        String normalizedDecision = safeTag(decision).toUpperCase(Locale.ROOT);
        String sourceTag = source != null ? source.name() : CloudMutationSource.UNKNOWN.name();
        String reasonTag = safeTag(reason);

        switch (normalizedDecision) {
            case "ALLOW" -> incrementCloudCounter(CLOUD_SCALE_ALLOWED, sourceTag, reasonTag);
            case "DRY_RUN" -> incrementCloudCounter(CLOUD_SCALE_DRY_RUN, sourceTag, reasonTag);
            default -> incrementCloudCounter(CLOUD_SCALE_DENIED, sourceTag, reasonTag);
        }

        Counter.builder(CLOUD_SCALE_SOURCE)
                .tag("source", sourceTag)
                .tag("decision", normalizedDecision)
                .tag("reason", reasonTag)
                .register(Metrics.globalRegistry)
                .increment();
    }

    public static void recordCsvParseFailure() {
        Metrics.counter(CSV_PARSE_FAILURES).increment();
    }

    public static void recordJsonParseFailure() {
        Metrics.counter(JSON_PARSE_FAILURES).increment();
    }

    private static void incrementCloudCounter(String metricName, String source, String reason) {
        Counter.builder(metricName)
                .tag("source", source)
                .tag("reason", reason)
                .register(Metrics.globalRegistry)
                .increment();
    }

    private static String safeTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }
}
