package core;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum RoutingStrategyId {
    TAIL_LATENCY_POWER_OF_TWO("TAIL_LATENCY_POWER_OF_TWO");

    private final String externalName;

    RoutingStrategyId(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }

    public static Optional<RoutingStrategyId> fromName(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(id -> id.name().equals(normalized) || id.externalName.equals(normalized))
                .findFirst();
    }

    @Override
    public String toString() {
        return externalName;
    }
}
