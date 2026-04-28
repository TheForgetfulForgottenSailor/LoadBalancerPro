package core;

public final class ScalingRecommendation {
    private final double unallocatedLoad;
    private final double targetCapacityPerServer;
    private final int additionalServers;

    private ScalingRecommendation(double unallocatedLoad, double targetCapacityPerServer, int additionalServers) {
        this.unallocatedLoad = sanitizeNonNegative(unallocatedLoad);
        this.targetCapacityPerServer = sanitizeNonNegative(targetCapacityPerServer);
        this.additionalServers = Math.max(0, additionalServers);
    }

    public static ScalingRecommendation forUnallocatedLoad(double unallocatedLoad, double targetCapacityPerServer) {
        if (!Double.isFinite(unallocatedLoad) || !Double.isFinite(targetCapacityPerServer)
                || unallocatedLoad <= 0.0 || targetCapacityPerServer <= 0.0) {
            return new ScalingRecommendation(unallocatedLoad, targetCapacityPerServer, 0);
        }
        double recommendedServers = Math.ceil(unallocatedLoad / targetCapacityPerServer);
        int additionalServers = recommendedServers > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) recommendedServers;
        return new ScalingRecommendation(unallocatedLoad, targetCapacityPerServer, additionalServers);
    }

    public double unallocatedLoad() {
        return unallocatedLoad;
    }

    public double targetCapacityPerServer() {
        return targetCapacityPerServer;
    }

    public int additionalServers() {
        return additionalServers;
    }

    private static double sanitizeNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
