package cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProgressAnimation {
    private static final Logger logger = LogManager.getLogger(ProgressAnimation.class);
    private final CliConfig config;
    private final String message;
    private final Thread animationThread;
    private volatile boolean running = false;

    public ProgressAnimation(CliConfig config, String message) {
        this.config = config;
        this.message = message;
        this.animationThread = new Thread(() -> {
            int i = 0;
            while (running) {
                System.out.print(message + config.getProgressAnimation()[i % config.getProgressAnimation().length] + "\r");
                i++;
                try {
                    Thread.sleep(config.getProgressAnimationSpeed());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void start() {
        running = true;
        animationThread.start();
    }

    public void stop() {
        running = false;
        animationThread.interrupt();
        try {
            animationThread.join();
            System.out.print("\r" + " ".repeat(message.length() + 1) + "\r"); // Clear line
        } catch (InterruptedException e) {
            logger.error("Animation thread join interrupted: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
}
