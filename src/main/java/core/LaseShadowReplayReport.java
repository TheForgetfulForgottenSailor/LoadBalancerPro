package core;

import java.util.List;
import java.util.Objects;

public record LaseShadowReplayReport(
        String sourceName,
        LaseShadowReplayMetrics metrics,
        List<String> warnings) {

    public LaseShadowReplayReport {
        sourceName = requireNonBlank(sourceName, "sourceName");
        Objects.requireNonNull(metrics, "metrics cannot be null");
        Objects.requireNonNull(warnings, "warnings cannot be null");
        warnings = List.copyOf(warnings);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }
}
