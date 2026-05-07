package com.richmond423.loadbalancerpro.core;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

final class CloudMetricsCoordinator {
    private static final int CLOUD_RETRY_ATTEMPTS = 3;
    private static final long CLOUD_RETRY_DELAY_MS = 1000;

    private final Supplier<CloudManager> cloudManagerSupplier;
    private final Logger logger;

    CloudMetricsCoordinator(Supplier<CloudManager> cloudManagerSupplier, Logger logger) {
        this.cloudManagerSupplier = Objects.requireNonNull(cloudManagerSupplier, "cloudManagerSupplier cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    void updateCloudMetricsIfAvailable() throws IOException {
        CloudManager cloudManager = cloudManagerSupplier.get();
        if (cloudManager == null) {
            logger.debug("CloudManager not initialized; skipping cloud metric update.");
            return;
        }
        int attempts = CLOUD_RETRY_ATTEMPTS;
        while (attempts > 0) {
            try {
                cloudManager.updateServerMetricsFromCloud();
                return;
            } catch (Exception e) {
                attempts--;
                logger.warn("Cloud metric update failed (attempts left: {}): {}", attempts, e.getMessage());
                if (attempts == 0) {
                    throw new IOException("Cloud metric update failed after retries", e);
                }
                try {
                    Thread.sleep(CLOUD_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during retry delay", ie);
                }
            }
        }
    }
}
