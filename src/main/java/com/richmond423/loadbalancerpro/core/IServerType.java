package com.richmond423.loadbalancerpro.core;

import java.util.Locale;
import org.json.JSONObject;

public interface IServerType {
    String name();
    String getDescription(Locale locale);
    JSONObject toJson();
}
