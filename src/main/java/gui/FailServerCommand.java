package gui;

import core.LoadBalancer;
import core.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Enhanced command to fail a server in LoadBalancerGUI with async support.
 */
public class FailServerCommand implements Command {
    private static final Logger logger = LogManager.getLogger(FailServerCommand.class);
    private final Server server;
    private final String id;
    private final boolean originalHealth;
    private final AtomicReference<Status> status;

    public FailServerCommand(Server server) {
        this.server = server;
        this.id = "Fail-" + server.getServerId() + "-" + System.nanoTime();
        this.originalHealth = server.isHealthy();
        this.status = new AtomicReference<>(Status.PENDING);
    }

    public FailServerCommand(LoadBalancer balancer, String serverId) {
        this(balancer.getServer(serverId));
    }

    @Override
    public void execute() {
        synchronized (server) {
            server.setHealthy(false);
            status.set(Status.COMPLETED);
            logger.info("Server {} failed.", server.getServerId());
        }
    }

    @Override
    public void undo() {
        synchronized (server) {
            if (!canUndo()) {
                logger.warn("Cannot undo command for server {}: not completed", server.getServerId());
                return;
            }
            server.setHealthy(originalHealth);
            status.set(Status.PENDING);
            logger.info("Server {} health restored to {}", server.getServerId(), originalHealth);
        }
    }

    @Override
    public boolean canUndo() {
        return status.get() == Status.COMPLETED;
    }

    @Override
    public String getDescription() {
        return "Failed server " + server.getServerId();
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
