package gui;

import core.Server;
import javafx.application.Platform;
import javafx.beans.property.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.beans.PropertyChangeListener;
import java.util.Objects;

/**
 * Enhanced JavaFX data model for Server objects in a TableView.
 * Incorporates automatic updates, encapsulation via read-only properties, change detection, and utility methods.
 */
public class ServerTableRow {
    private static final Logger logger = LogManager.getLogger(ServerTableRow.class);

    private final Server source;
    private final ReadOnlyStringWrapper serverId = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper serverType = new ReadOnlyStringWrapper();
    private final ReadOnlyDoubleWrapper cpuUsage = new ReadOnlyDoubleWrapper();
    private final ReadOnlyDoubleWrapper memoryUsage = new ReadOnlyDoubleWrapper();
    private final ReadOnlyDoubleWrapper diskUsage = new ReadOnlyDoubleWrapper();
    private final ReadOnlyDoubleWrapper capacity = new ReadOnlyDoubleWrapper();
    private final ReadOnlyDoubleWrapper loadScore = new ReadOnlyDoubleWrapper();
    private final ReadOnlyBooleanWrapper healthy = new ReadOnlyBooleanWrapper();
    private final PropertyChangeListener serverChangeListener = evt -> updateSafelyFromServer();

    public ServerTableRow(Server server) {
        if (server == null) {
            throw new IllegalArgumentException("Server cannot be null");
        }
        this.source = server;

        try {
            serverId.set(server.getServerId());
            serverType.set(server.getServerType().toString());
            syncProperties();
            server.addPropertyChangeListener(serverChangeListener);
            logger.debug("Initialized ServerTableRow for server: {}", server.getServerId());
        } catch (Exception e) {
            logger.error("Failed to initialize ServerTableRow for server: {}", server.getServerId(), e);
            throw new RuntimeException("Initialization failed", e);
        }
    }

    private void syncProperties() {
        updatePropertyIfChanged(serverId, source.getServerId());
        updatePropertyIfChanged(serverType, source.getServerType().toString());
        updatePropertyIfChanged(cpuUsage, source.getCpuUsage());
        updatePropertyIfChanged(memoryUsage, source.getMemoryUsage());
        updatePropertyIfChanged(diskUsage, source.getDiskUsage());
        updatePropertyIfChanged(capacity, source.getCapacity());
        updatePropertyIfChanged(loadScore, source.getLoadScore());
        updatePropertyIfChanged(healthy, source.isHealthy());
    }

    public void updateSafelyFromServer() {
        if (Platform.isFxApplicationThread()) {
            syncProperties();
        } else {
            Platform.runLater(() -> {
                syncProperties();
                logger.debug("Updated ServerTableRow for server: {}", serverId.get());
            });
        }
    }

    private <T> void updatePropertyIfChanged(Property<T> property, T newValue) {
        if (!Objects.equals(property.getValue(), newValue)) {
            T oldValue = property.getValue();
            property.setValue(newValue);
            logger.trace("Property updated for server {}: {} from {} to {}", serverId.get(), property.getName(), oldValue, newValue);
        }
    }

    public void close() {
        source.removePropertyChangeListener(serverChangeListener);
        logger.debug("Closed ServerTableRow for server: {}", serverId.get());
    }

    public String getHealthStatusString() {
        return isHealthy() ? "✅ Healthy" : "⚠️ Unhealthy";
    }

    // Read-only JavaFX property accessors
    public ReadOnlyStringProperty serverIdProperty() { return serverId.getReadOnlyProperty(); }
    public ReadOnlyStringProperty serverTypeProperty() { return serverType.getReadOnlyProperty(); }
    public ReadOnlyDoubleProperty cpuUsageProperty() { return cpuUsage.getReadOnlyProperty(); }
    public ReadOnlyDoubleProperty memoryUsageProperty() { return memoryUsage.getReadOnlyProperty(); }
    public ReadOnlyDoubleProperty diskUsageProperty() { return diskUsage.getReadOnlyProperty(); }
    public ReadOnlyDoubleProperty capacityProperty() { return capacity.getReadOnlyProperty(); }
    public ReadOnlyDoubleProperty loadScoreProperty() { return loadScore.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty healthyProperty() { return healthy.getReadOnlyProperty(); }

    // Standard getters
    public String getServerId() { return serverId.get(); }
    public String getServerType() { return serverType.get(); }
    public double getCpuUsage() { return cpuUsage.get(); }
    public double getMemoryUsage() { return memoryUsage.get(); }
    public double getDiskUsage() { return diskUsage.get(); }
    public double getCapacity() { return capacity.get(); }
    public double getLoadScore() { return loadScore.get(); }
    public boolean isHealthy() { return healthy.get(); }

    public Server getSourceServer() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerTableRow)) return false;
        ServerTableRow that = (ServerTableRow) o;
        return source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    @Override
    public String toString() {
        return String.format("ServerTableRow{id='%s', type='%s', cpu=%.2f%%, mem=%.2f%%, disk=%.2f%%, cap=%.2f, load=%.2f, health=%s}",
                getServerId(), getServerType(), getCpuUsage(), getMemoryUsage(), getDiskUsage(), getCapacity(), getLoadScore(), getHealthStatusString());
    }
}
