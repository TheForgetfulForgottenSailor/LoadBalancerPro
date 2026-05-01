package core;

import java.util.Objects;

public record LaseShadowReplayRecord(
        int schemaVersion,
        LaseShadowEvent event) {

    public static final int SCHEMA_VERSION = 1;

    public LaseShadowReplayRecord {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported replay schema version: " + schemaVersion);
        }
        Objects.requireNonNull(event, "event cannot be null");
    }

    public static LaseShadowReplayRecord fromEvent(LaseShadowEvent event) {
        return new LaseShadowReplayRecord(SCHEMA_VERSION, event);
    }
}
