package api;

import cli.LaseDemoCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LoadBalancerApiApplication {
    public static void main(String[] args) {
        if (!shouldStartApi(args)) {
            LaseDemoCommand.Result demoResult = LaseDemoCommand.runIfRequested(args, System.out, System.err);
            if (demoResult.exitCode() != 0) {
                System.exit(demoResult.exitCode());
            }
            return;
        }
        SpringApplication.run(LoadBalancerApiApplication.class, args);
    }

    static boolean shouldStartApi(String[] args) {
        return !LaseDemoCommand.isRequested(args);
    }
}
