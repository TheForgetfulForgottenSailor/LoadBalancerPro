package gui;

import core.LoadBalancer;
import core.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Command to add a server in LoadBalancerGUI with async support and chaining potential.
 * Integrates with real-time GUI updates and command history for undoable server additions.
 */
public class AddServerCommand implements Command {
    private static final Logger logger = LogManager.getLogger(AddServerCommand.class);
    private final LoadBalancer balancer;
    private final Server server;
    private final String id;
    private final AtomicReference<Status> status;

    public AddServerCommand(LoadBalancer balancer, Server server) {
        this.balancer = Objects.requireNonNull(balancer, "Balancer cannot be null");
        this.server = Objects.requireNonNull(server, "Server cannot be null");
        this.id = "Add-" + server.getServerId() + "-" + System.nanoTime();
        this.status = new AtomicReference<>(Status.PENDING);
    }

    @Override
    public void execute() {
        synchronized (balancer) {
            try {
                balancer.addServer(server);
                status.set(Status.COMPLETED);
                logger.info("Server {} added successfully.", server.getServerId());
            } catch (Exception e) {
                status.set(Status.FAILED);
                logger.error("Failed to add server {}: {}", server.getServerId(), e.getMessage());
                throw e;
            }
        }
    }

    @Override
    public void undo() {
        synchronized (balancer) {
            if (!canUndo()) {
                logger.warn("Cannot undo add server {}: status is {}", server.getServerId(), status.get());
                return;
            }
            try {
                balancer.removeServer(server.getServerId());
                status.set(Status.PENDING);
                logger.info("Server {} removed via undo.", server.getServerId());
            } catch (Exception e) {
                status.set(Status.FAILED);
                logger.error("Failed to undo server addition {}: {}", server.getServerId(), e.getMessage());
                throw e;
            }
        }
    }

    @Override
    public boolean canUndo() {
        return status.get() == Status.COMPLETED && balancer.getServerMap().containsKey(server.getServerId());
    }

    @Override
    public String getDescription() {
        return "Added server " + server.getServerId();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Status getStatus() {
        return status.get();
    }
}
