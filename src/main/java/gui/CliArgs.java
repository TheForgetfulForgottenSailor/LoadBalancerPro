package gui;

/**
 * Enum for CLI argument names used in GuiConfig.
 */
public enum CliArgs {
    GUI_REFRESH_INTERVAL("--gui-refresh-interval"),
    GUI_MIN_WINDOW_WIDTH("--gui-min-window-width"),
    GUI_MIN_WINDOW_HEIGHT("--gui-min-window-height"),
    GUI_DEFAULT_WINDOW_WIDTH("--gui-default-window-width"),
    GUI_DEFAULT_WINDOW_HEIGHT("--gui-default-window-height"),
    GUI_SERVERS_PER_PAGE("--gui-servers-per-page"),
    GUI_MAX_BARS_IN_CHART("--gui-max-bars-in-chart"),
    GUI_MAX_ALERTS_DISPLAYED("--gui-max-alerts-displayed"),
    GUI_MAX_UNDO_HISTORY_SIZE("--gui-max-undo-history-size"),
    GUI_MAX_RETRIES("--gui-max-retries"),
    GUI_TABLE_VIEW_HEIGHT("--gui-table-view-height"),
    GUI_CHART_HEIGHT("--gui-chart-height"),
    GUI_TEXT_AREA_HEIGHT("--gui-text-area-height");

    private final String arg;

    CliArgs(String arg) {
        this.arg = arg;
    }

    public String arg() {
        return arg;
    }
}
