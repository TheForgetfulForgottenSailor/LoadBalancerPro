package api;

import cli.LaseDemoCommand;
import cli.LaseReplayCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LoadBalancerApiApplication {
    private static final String FALLBACK_VERSION = "1.1.1";

    public static void main(String[] args) {
        if (isVersionRequested(args)) {
            System.out.println("LoadBalancerPro version " + version());
            return;
        }
        if (!shouldStartApi(args)) {
            LaseReplayCommand.Result replayResult = LaseReplayCommand.runIfRequested(args, System.out, System.err);
            if (replayResult.requested()) {
                if (replayResult.exitCode() != 0) {
                    System.exit(replayResult.exitCode());
                }
                return;
            }
            LaseDemoCommand.Result demoResult = LaseDemoCommand.runIfRequested(args, System.out, System.err);
            if (demoResult.exitCode() != 0) {
                System.exit(demoResult.exitCode());
            }
            return;
        }
        SpringApplication.run(LoadBalancerApiApplication.class, args);
    }

    static boolean shouldStartApi(String[] args) {
        return !isVersionRequested(args) && !LaseDemoCommand.isRequested(args) && !LaseReplayCommand.isRequested(args);
    }

    static boolean isVersionRequested(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if ("--version".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    static String version() {
        String implementationVersion = LoadBalancerApiApplication.class.getPackage().getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? FALLBACK_VERSION
                : implementationVersion;
    }
}
