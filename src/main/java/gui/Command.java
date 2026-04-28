package gui;

import org.json.JSONObject;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Command interface for LoadBalancerGUI operations.
 * Supports async execution and serialization for real-time GUI integration.
 */
public interface Command {
    void execute();
    void undo();
    boolean canUndo();
    String getDescription();
    Status getStatus();
    String getId();

    default CompletableFuture<Void> executeAsync() {
        return CompletableFuture.runAsync(this::execute);
    }

    default CompletableFuture<Void> undoAsync() {
        return CompletableFuture.runAsync(() -> {
            if (canUndo()) undo();
        });
    }

    default JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("id", getId());
        json.put("description", getDescription());
        json.put("status", getStatus().name());
        json.put("version", 2);
        return json;
    }

    enum Status {
        PENDING, COMPLETED, FAILED
    }
}
