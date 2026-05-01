package core;

public record LaseShadowReplayScoreSummary(
        long count,
        double min,
        double average,
        double max) {

    public LaseShadowReplayScoreSummary {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        requireFiniteNonNegative(min, "min");
        requireFiniteNonNegative(average, "average");
        requireFiniteNonNegative(max, "max");
        if (count == 0 && (min != 0.0 || average != 0.0 || max != 0.0)) {
            throw new IllegalArgumentException("empty score summary values must be zero");
        }
        if (count > 0 && (min > average || average > max)) {
            throw new IllegalArgumentException("score summary must satisfy min <= average <= max");
        }
    }

    public static LaseShadowReplayScoreSummary empty() {
        return new LaseShadowReplayScoreSummary(0, 0.0, 0.0, 0.0);
    }

    static LaseShadowReplayScoreSummary from(ScoreAccumulator accumulator) {
        if (accumulator.count == 0) {
            return empty();
        }
        return new LaseShadowReplayScoreSummary(accumulator.count, accumulator.min,
                accumulator.total / accumulator.count, accumulator.max);
    }

    static final class ScoreAccumulator {
        private long count;
        private double total;
        private double min = Double.POSITIVE_INFINITY;
        private double max;

        void record(Double value) {
            if (value == null) {
                return;
            }
            requireFiniteNonNegative(value, "score");
            count++;
            total += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
    }

    private static void requireFiniteNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }
}
