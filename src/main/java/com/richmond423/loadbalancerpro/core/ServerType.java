package com.richmond423.loadbalancerpro.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public enum ServerType implements IServerType {
    CLOUD("server.type.cloud", "Cloud-based virtual server"),
    ONSITE("server.type.onsite", "Physical on-premises server");

    private static final Logger logger = LogManager.getLogger(ServerType.class);
    private static final ResourceBundle DEFAULT_BUNDLE = ResourceBundle.getBundle("server_types", Locale.ENGLISH);
    private static final Pattern TYPE_NAME_PATTERN = Pattern.compile("^[A-Z0-9_]+$");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("^[A-Za-z0-9 .,-]+$");
    private static final Map<String, IServerType> NAME_TO_TYPE = new ConcurrentHashMap<>();
    private static final Map<String, ExtendedServerType> EXTENDED_TYPES = new ConcurrentHashMap<>();
    private static final Map<Locale, ResourceBundle> I18N_BUNDLES = new ConcurrentHashMap<>();
    private static final int MAX_DESCRIPTION_LENGTH = 200;
    private static final int MIN_JSON_VERSION = 1;
    private static final int MAX_JSON_VERSION = 3;
    private static int JSON_VERSION = 2; // Configurable within bounds

    // Metrics counters (simplified, could integrate with Micrometer)
    private static long customRegisteredCount = 0;
    private static long customUnregisteredCount = 0;
    private static long deserializationCount = 0;

    static {
        Arrays.stream(values()).forEach(type -> NAME_TO_TYPE.put(type.name(), type));
        I18N_BUNDLES.put(Locale.ENGLISH, DEFAULT_BUNDLE);
    }

    private final String i18nKey;
    private final String defaultDescription;
    private final String cachedToString;

    ServerType(String i18nKey, String defaultDescription) {
        validateDescription(defaultDescription);
        this.i18nKey = i18nKey;
        this.defaultDescription = defaultDescription;
        this.cachedToString = name() + " (" + defaultDescription + ")";
    }

    @Override
    public String getDescription(Locale locale) {
        ResourceBundle bundle = I18N_BUNDLES.computeIfAbsent(locale, this::loadBundle);
        try {
            if (!bundle.containsKey(i18nKey)) {
                logger.warn("Missing i18n key '{}' in bundle for locale {}; using default: {}", 
                            i18nKey, locale, defaultDescription);
            }
            return bundle.getString(i18nKey);
        } catch (MissingResourceException e) {
            logger.warn("Failed to load description for key '{}' in locale {}; using default: {}", 
                        i18nKey, locale, defaultDescription);
            return defaultDescription;
        }
    }

    private ResourceBundle loadBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle("server_types", locale);
        } catch (MissingResourceException e) {
            logger.warn("No i18n bundle found for locale {}; falling back to English", locale);
            return DEFAULT_BUNDLE;
        }
    }

    @Override
    public String toString() {
        return cachedToString;
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("name", name());
        json.put("description", defaultDescription);
        json.put("version", JSON_VERSION);
        return json;
    }

    /**
     * Deserializes a server type from JSON.
     * Thread-safe: Yes
     */
    public static IServerType fromJson(JSONObject json) {
        deserializationCount++;
        if (json == null) {
            logger.warn("Null JSON object provided for deserialization; defaulting to ONSITE");
            return ONSITE;
        }
        String name = json.optString("name", "ONSITE").toUpperCase();
        int version = json.optInt("version", 1);
        if (version < MIN_JSON_VERSION || version > MAX_JSON_VERSION) {
            logger.warn("Unsupported JSON version {} for type '{}'; defaulting to ONSITE", version, name);
            return ONSITE;
        }
        IServerType type = NAME_TO_TYPE.get(name);
        if (type != null) {
            return type;
        }
        type = EXTENDED_TYPES.computeIfAbsent(name, k -> ExtendedServerType.fromJson(json));
        if (type == null) {
            logger.warn("Unknown server type '{}' in JSON (version {}); defaulting to ONSITE", name, version);
            return ONSITE;
        }
        return type;
    }

    /**
     * Registers a custom server type.
     * Thread-safe: Yes
     */
    public static synchronized IServerType registerCustomType(String name, String description, String i18nKey) {
        if (name == null || name.trim().isEmpty() || !TYPE_NAME_PATTERN.matcher(name.toUpperCase()).matches()) {
            logger.warn("Invalid custom server type name '{}'; must be alphanumeric with underscores; defaulting to ONSITE", name);
            return ONSITE;
        }
        validateDescription(description);
        String upperName = name.toUpperCase();
        if (NAME_TO_TYPE.containsKey(upperName)) {
            logger.warn("Duplicate server type '{}'; returning existing type", upperName);
            return NAME_TO_TYPE.get(upperName);
        }
        ExtendedServerType type = new ExtendedServerType(upperName, description, i18nKey);
        EXTENDED_TYPES.put(upperName, type);
        customRegisteredCount++;
        logger.info("Registered custom server type: {}", upperName);
        return type;
    }

    /**
     * Registers multiple custom server types.
     * Thread-safe: Yes
     */
    public static synchronized List<IServerType> registerCustomTypes(Map<String, String> types, Map<String, String> i18nKeys) {
        List<IServerType> registeredTypes = new ArrayList<>();
        if (types == null) return registeredTypes;
        
        for (Map.Entry<String, String> entry : types.entrySet()) {
            String name = entry.getKey();
            String description = entry.getValue();
            String i18nKey = i18nKeys != null ? i18nKeys.get(name) : null;
            IServerType type = registerCustomType(name, description, i18nKey);
            registeredTypes.add(type);
        }
        return registeredTypes;
    }

    /**
     * Unregisters a custom server type.
     * Thread-safe: Yes
     */
    public static synchronized boolean unregisterCustomType(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String upperName = name.toUpperCase();
        if (NAME_TO_TYPE.containsKey(upperName)) {
            logger.warn("Cannot unregister standard type '{}'", upperName);
            return false;
        }
        ExtendedServerType removed = EXTENDED_TYPES.remove(upperName);
        if (removed != null) {
            customUnregisteredCount++;
            logger.info("Unregistered custom server type: {}", upperName);
            return true;
        }
        return false;
    }

    /**
     * Saves custom types to a JSON file.
     * Thread-safe: Yes
     */
    public static synchronized void saveCustomTypes(String filePath) throws IOException {
        JSONArray jsonArray = new JSONArray();
        EXTENDED_TYPES.values().forEach(type -> jsonArray.put(type.toJson()));
        try (Writer writer = new FileWriter(filePath)) {
            jsonArray.write(writer, 2, 0);
            logger.info("Saved {} custom server types to {}", EXTENDED_TYPES.size(), filePath);
        }
    }

    /**
     * Loads custom types from a JSON file.
     * Thread-safe: Yes
     */
    public static synchronized void loadCustomTypes(String filePath) throws IOException {
        if (!Files.exists(Paths.get(filePath))) {
            logger.info("No custom types file found at {}", filePath);
            return;
        }
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        JSONArray jsonArray = new JSONArray(content);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject json = jsonArray.getJSONObject(i);
            IServerType type = fromJson(json);
            if (type instanceof ExtendedServerType && !NAME_TO_TYPE.containsKey(type.name())) {
                EXTENDED_TYPES.put(type.name(), (ExtendedServerType) type);
                customRegisteredCount++;
            }
        }
        logger.info("Loaded {} custom server types from {}", jsonArray.length(), filePath);
    }

    /**
     * Validates localization for a given locale, throwing an exception if keys are missing.
     * Thread-safe: Yes
     */
    public static synchronized void validateLocalization(Locale locale) {
        ResourceBundle bundle = I18N_BUNDLES.computeIfAbsent(locale, l -> {
            try {
                return ResourceBundle.getBundle("server_types", l);
            } catch (MissingResourceException e) {
                throw new IllegalStateException("No i18n bundle found for locale: " + l);
            }
        });
        List<String> missingKeys = new ArrayList<>();
        for (ServerType type : values()) {
            if (!bundle.containsKey(type.i18nKey)) {
                missingKeys.add(type.i18nKey);
            }
        }
        EXTENDED_TYPES.values().forEach(type -> {
            if (type.i18nKey != null && !bundle.containsKey(type.i18nKey)) {
                missingKeys.add(type.i18nKey);
            }
        });
        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException("Missing i18n keys for locale " + locale + ": " + missingKeys);
        }
        logger.info("Localization validated for locale: {}", locale);
    }

    private static void validateDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description exceeds max length of " + MAX_DESCRIPTION_LENGTH);
        }
        Pattern descriptionPattern = DESCRIPTION_PATTERN != null
            ? DESCRIPTION_PATTERN
            : Pattern.compile("^[A-Za-z0-9 .,-]+$");
        if (!descriptionPattern.matcher(description).matches()) {
            throw new IllegalArgumentException("Description contains invalid characters; allowed: A-Z, a-z, 0-9, space, dot, comma, hyphen");
        }
    }

    /**
     * Looks up a server type by name.
     * Thread-safe: Yes
     */
    public static IServerType fromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            logger.warn("Null or empty server type name; defaulting to ONSITE");
            return ONSITE;
        }
        String upperName = name.toUpperCase();
        IServerType type = NAME_TO_TYPE.getOrDefault(upperName, EXTENDED_TYPES.get(upperName));
        if (type == null) {
            logger.warn("Unknown server type '{}'; defaulting to ONSITE", name);
            return ONSITE;
        }
        return type;
    }

    public static boolean isValidType(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String upperName = name.toUpperCase();
        return NAME_TO_TYPE.containsKey(upperName) || EXTENDED_TYPES.containsKey(upperName);
    }

    /**
     * Sets the JSON version within allowed bounds.
     * Thread-safe: Yes
     */
    public static synchronized void setJsonVersion(int version) {
        if (version < MIN_JSON_VERSION || version > MAX_JSON_VERSION) {
            throw new IllegalArgumentException(
                "JSON version must be between " + MIN_JSON_VERSION + " and " + MAX_JSON_VERSION);
        }
        JSON_VERSION = version;
        logger.info("Set JSON version to {}", version);
    }

    // Metrics accessors
    public static long getCustomRegisteredCount() { return customRegisteredCount; }
    public static long getCustomUnregisteredCount() { return customUnregisteredCount; }
    public static long getDeserializationCount() { return deserializationCount; }

    private static class ExtendedServerType implements IServerType {
        private final String name;
        private final String i18nKey;
        private final String defaultDescription;
        private final String cachedToString;

        ExtendedServerType(String name, String description, String i18nKey) {
            validateDescription(description);
            this.name = name;
            this.i18nKey = i18nKey;
            this.defaultDescription = description;
            this.cachedToString = name + " (" + description + ")";
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String getDescription(Locale locale) {
            if (i18nKey == null) {
                return defaultDescription;
            }
            ResourceBundle bundle = I18N_BUNDLES.computeIfAbsent(locale, l -> {
                try {
                    return ResourceBundle.getBundle("server_types", l);
                } catch (MissingResourceException e) {
                    logger.warn("No i18n bundle found for locale {}; falling back to English", l);
                    return DEFAULT_BUNDLE;
                }
            });
            try {
                if (!bundle.containsKey(i18nKey)) {
                    logger.warn("Missing i18n key '{}' for extended type '{}' in locale {}; using default: {}", 
                                i18nKey, name, locale, defaultDescription);
                }
                return bundle.getString(i18nKey);
            } catch (MissingResourceException e) {
                logger.warn("Failed to load description for key '{}' in locale {} for extended type '{}'; using default: {}", 
                            i18nKey, locale, name, defaultDescription);
                return defaultDescription;
            }
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("description", defaultDescription);
            json.put("i18nKey", i18nKey != null ? i18nKey : JSONObject.NULL);
            json.put("version", JSON_VERSION);
            return json;
        }

        @Override
        public String toString() {
            return cachedToString;
        }

        public static ExtendedServerType fromJson(JSONObject json) {
            String name = json.optString("name", "UNKNOWN").toUpperCase();
            String description = json.optString("description", "Unknown server type");
            String i18nKey = json.has("i18nKey") && !json.isNull("i18nKey") ? json.getString("i18nKey") : null;
            if (!TYPE_NAME_PATTERN.matcher(name).matches()) {
                logger.warn("Invalid extended type name '{}'; skipping creation", name);
                return null;
            }
            return new ExtendedServerType(name, description, i18nKey);
        }
    }

    /*
     * Expected server_types.properties format:
     * # Standard types
     * server.type.cloud=Cloud-based virtual server
     * server.type.onsite=Physical on-premises server
     * # Extended type example
     * server.type.extended.azure=Azure cloud server
     * # Each line should follow: key=description
     */
}
