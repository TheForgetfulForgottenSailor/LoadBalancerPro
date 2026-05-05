package com.richmond423.loadbalancerpro.util;

import com.richmond423.loadbalancerpro.core.Server;
import com.richmond423.loadbalancerpro.core.ServerType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class JsonServerLogParser {
    private static final Set<String> REPORT_FIELDS = Set.of("version", "timestamp", "servers", "alerts");
    private static final Set<String> SERVER_FIELDS = Set.of(
            "version",
            "serverId",
            "cpuUsage",
            "memoryUsage",
            "diskUsage",
            "loadScore",
            "weight",
            "capacity",
            "healthy",
            "serverType",
            "healthThreshold",
            "cpuHistory",
            "memHistory",
            "diskHistory",
            "snapshot",
            "cloudInstance");
    private static final Set<String> SNAPSHOT_FIELDS = Set.of(
            "cpuUsage",
            "memoryUsage",
            "diskUsage",
            "loadScore",
            "weight",
            "capacity",
            "healthy",
            "serverType",
            "cpuHistory",
            "memHistory",
            "diskHistory",
            "historyIndex");

    private JsonServerLogParser() {
    }

    static JSONArray parseArray(String jsonContent) {
        return new JSONArray(jsonContent);
    }

    static ParsedDocument parseDocument(String jsonContent, int defaultVersion) {
        JSONTokener tokener = new JSONTokener(jsonContent);
        Object root = tokener.nextValue();
        if (tokener.nextClean() != 0) {
            throw schemaError("document", "must contain a single JSON value");
        }
        if (root instanceof JSONArray array) {
            return parseLegacyArray(array, defaultVersion);
        }
        if (root instanceof JSONObject report) {
            return parseReport(report, defaultVersion);
        }
        throw new IllegalArgumentException("JSON document must be an array or report object");
    }

    static ParsedServer parseEntry(JSONArray jsonArray, int index, int defaultVersion) {
        JSONObject json = jsonArray.getJSONObject(index);
        validateServer(json, "servers[" + index + "]");
        int version = json.optInt("version", defaultVersion);
        return new ParsedServer(Server.fromJson(json), version);
    }

    private static ParsedDocument parseLegacyArray(JSONArray array, int defaultVersion) {
        List<ParsedServer> servers = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            servers.add(parseEntry(array, i, defaultVersion));
        }
        return new ParsedDocument(defaultVersion, servers, List.of());
    }

    private static ParsedDocument parseReport(JSONObject report, int defaultVersion) {
        validateKeys(report, REPORT_FIELDS, "report");
        requireNumber(report, "version", "report");
        requireString(report, "timestamp", "report");
        if (!report.has("servers") || !(report.get("servers") instanceof JSONArray)) {
            throw schemaError("report.servers", "must be an array");
        }
        if (!report.has("alerts") || !(report.get("alerts") instanceof JSONArray)) {
            throw schemaError("report.alerts", "must be an array");
        }

        JSONArray serverArray = report.getJSONArray("servers");
        List<ParsedServer> servers = new ArrayList<>(serverArray.length());
        for (int i = 0; i < serverArray.length(); i++) {
            servers.add(parseEntry(serverArray, i, defaultVersion));
        }

        JSONArray alertArray = report.getJSONArray("alerts");
        List<String> alerts = new ArrayList<>(alertArray.length());
        for (int i = 0; i < alertArray.length(); i++) {
            Object alert = alertArray.get(i);
            if (!(alert instanceof String)) {
                throw schemaError("report.alerts[" + i + "]", "must be a string");
            }
            alerts.add((String) alert);
        }
        return new ParsedDocument(report.getInt("version"), servers, alerts);
    }

    private static void validateServer(JSONObject json, String path) {
        validateKeys(json, SERVER_FIELDS, path);
        requireString(json, "serverId", path);
        if (json.getString("serverId").isBlank()) {
            throw schemaError(path + ".serverId", "must not be blank");
        }
        requireNumber(json, "cpuUsage", path);
        requireNumber(json, "memoryUsage", path);
        requireNumber(json, "diskUsage", path);
        requireOptionalNumber(json, "version", path);
        requireOptionalNumber(json, "loadScore", path);
        requireOptionalNumber(json, "weight", path);
        requireOptionalNumber(json, "capacity", path);
        requireOptionalNumber(json, "healthThreshold", path);
        requireOptionalBoolean(json, "healthy", path);
        requireOptionalBoolean(json, "cloudInstance", path);
        requireOptionalMetricArray(json, "cpuHistory", path);
        requireOptionalMetricArray(json, "memHistory", path);
        requireOptionalMetricArray(json, "diskHistory", path);
        if (json.has("serverType")) {
            requireString(json, "serverType", path);
            try {
                ServerType.valueOf(json.getString("serverType"));
            } catch (IllegalArgumentException e) {
                throw schemaError(path + ".serverType", "must be a known server type");
            }
        }
        if (json.has("snapshot")) {
            if (!(json.get("snapshot") instanceof JSONObject snapshot)) {
                throw schemaError(path + ".snapshot", "must be an object");
            }
            validateSnapshot(snapshot, path + ".snapshot");
        }
    }

    private static void validateSnapshot(JSONObject snapshot, String path) {
        validateKeys(snapshot, SNAPSHOT_FIELDS, path);
        requireOptionalNumber(snapshot, "cpuUsage", path);
        requireOptionalNumber(snapshot, "memoryUsage", path);
        requireOptionalNumber(snapshot, "diskUsage", path);
        requireOptionalNumber(snapshot, "loadScore", path);
        requireOptionalNumber(snapshot, "weight", path);
        requireOptionalNumber(snapshot, "capacity", path);
        requireOptionalBoolean(snapshot, "healthy", path);
        requireOptionalMetricArray(snapshot, "cpuHistory", path);
        requireOptionalMetricArray(snapshot, "memHistory", path);
        requireOptionalMetricArray(snapshot, "diskHistory", path);
        requireOptionalNumber(snapshot, "historyIndex", path);
        if (snapshot.has("serverType")) {
            requireString(snapshot, "serverType", path);
            try {
                ServerType.valueOf(snapshot.getString("serverType"));
            } catch (IllegalArgumentException e) {
                throw schemaError(path + ".serverType", "must be a known server type");
            }
        }
    }

    private static void validateKeys(JSONObject object, Set<String> allowedFields, String path) {
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!allowedFields.contains(key)) {
                throw schemaError(path + "." + key, "unexpected field");
            }
        }
    }

    private static void requireString(JSONObject object, String field, String path) {
        if (!object.has(field) || !(object.get(field) instanceof String)) {
            throw schemaError(path + "." + field, "must be a string");
        }
    }

    private static void requireNumber(JSONObject object, String field, String path) {
        if (!object.has(field) || !(object.get(field) instanceof Number)) {
            throw schemaError(path + "." + field, "must be numeric");
        }
        double value = object.getNumber(field).doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw schemaError(path + "." + field, "must be finite");
        }
    }

    private static void requireOptionalNumber(JSONObject object, String field, String path) {
        if (object.has(field)) {
            requireNumber(object, field, path);
        }
    }

    private static void requireOptionalBoolean(JSONObject object, String field, String path) {
        if (object.has(field) && !(object.get(field) instanceof Boolean)) {
            throw schemaError(path + "." + field, "must be boolean");
        }
    }

    private static void requireOptionalMetricArray(JSONObject object, String field, String path) {
        if (!object.has(field)) {
            return;
        }
        if (!(object.get(field) instanceof JSONArray array)) {
            throw schemaError(path + "." + field, "must be an array");
        }
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (!(value instanceof Number)) {
                throw schemaError(path + "." + field + "[" + i + "]", "must be numeric");
            }
        }
    }

    private static IllegalArgumentException schemaError(String field, String reason) {
        return new IllegalArgumentException("JSON schema violation: field=" + field + ", reason=" + reason);
    }

    static final class ParsedDocument {
        private final int version;
        private final List<ParsedServer> servers;
        private final List<String> alerts;

        private ParsedDocument(int version, List<ParsedServer> servers, List<String> alerts) {
            this.version = version;
            this.servers = List.copyOf(servers);
            this.alerts = List.copyOf(alerts);
        }

        int getVersion() {
            return version;
        }

        List<ParsedServer> getServers() {
            return servers;
        }

        List<String> getAlerts() {
            return alerts;
        }
    }

    static final class ParsedServer {
        private final Server server;
        private final int version;

        private ParsedServer(Server server, int version) {
            this.server = server;
            this.version = version;
        }

        Server getServer() {
            return server;
        }

        int getVersion() {
            return version;
        }
    }
}
