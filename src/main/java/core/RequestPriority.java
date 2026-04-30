package core;

import java.util.Objects;

public enum RequestPriority {
    CRITICAL(4),
    USER(3),
    BACKGROUND(2),
    PREFETCH(1);

    private final int importanceRank;

    RequestPriority(int importanceRank) {
        this.importanceRank = importanceRank;
    }

    public int importanceRank() {
        return importanceRank;
    }

    public boolean isMoreImportantThan(RequestPriority other) {
        Objects.requireNonNull(other, "other priority cannot be null");
        return importanceRank > other.importanceRank;
    }
}
