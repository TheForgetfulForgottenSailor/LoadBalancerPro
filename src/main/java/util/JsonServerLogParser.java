package util;

import core.Server;
import org.json.JSONArray;
import org.json.JSONObject;

final class JsonServerLogParser {
    private JsonServerLogParser() {
    }

    static JSONArray parseArray(String jsonContent) {
        return new JSONArray(jsonContent);
    }

    static ParsedServer parseEntry(JSONArray jsonArray, int index, int defaultVersion) {
        JSONObject json = jsonArray.getJSONObject(index);
        int version = json.optInt("version", defaultVersion);
        return new ParsedServer(Server.fromJson(json), version);
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
