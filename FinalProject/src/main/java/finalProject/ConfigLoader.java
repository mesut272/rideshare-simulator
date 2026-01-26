package finalProject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads and manages application configuration from config.properties file.
 * Provides type-safe access to configuration values with defaults.
 */
public class ConfigLoader {

    private static final String CONFIG_FILE = "config.properties";
    private static Properties properties;

    static {
        loadConfig();
    }

    /**
     * Load configuration from properties file
     */
    private static void loadConfig() {
        properties = new Properties();
        try (InputStream input = ConfigLoader.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {
                System.err.println("Warning: " + CONFIG_FILE + " not found. Using default values.");
                return;
            }

            properties.load(input);
            System.out.println("[CONFIG] Loaded configuration from " + CONFIG_FILE);

        } catch (IOException e) {
            System.err.println("Error loading config file: " + e.getMessage());
            System.err.println("Using default values.");
        }
    }

    /**
     * Get integer property with default value
     */
    private static int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Invalid value for " + key + ": " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get long property with default value
     */
    private static long getLongProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Invalid value for " + key + ": " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get string property with default value
     */
    private static String getStringProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue).trim();
    }

    /**
     * Get boolean property with default value
     */
    private static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    // ========================================
    // Public Configuration Getters
    // ========================================

    public static int getDriverCount() {
        return getIntProperty("simulation.driver.count", 3);
    }

    public static long getRequestIntervalMs() {
        return getLongProperty("simulation.request.interval.ms", 2000L);
    }

    public static int getMaxRequests() {
        return getIntProperty("simulation.max.requests", 10);
    }

    public static String getDispatchStrategy() {
        return getStringProperty("simulation.dispatch.strategy", "COMPOSITE");
    }

    public static String getLogLevel() {
        return getStringProperty("logging.level", "INFO");
    }

    public static String getLogFilePath() {
        return getStringProperty("logging.file.path", "logs/simulation.log");
    }

    public static boolean isMetricsEnabled() {
        return getBooleanProperty("metrics.enabled", true);
    }

    public static boolean isDetailedLoggingEnabled() {
        return getBooleanProperty("metrics.detailed.logging", true);
    }

    /**
     * Print all loaded configuration (for debugging)
     */
    public static void printConfig() {
        System.out.println("\n========== Configuration ==========");
        System.out.println("Driver Count:          " + getDriverCount());
        System.out.println("Request Interval (ms): " + getRequestIntervalMs());
        System.out.println("Max Requests:          " + getMaxRequests());
        System.out.println("Dispatch Strategy:     " + getDispatchStrategy());
        System.out.println("Log Level:             " + getLogLevel());
        System.out.println("Log File Path:         " + getLogFilePath());
        System.out.println("Metrics Enabled:       " + isMetricsEnabled());
        System.out.println("Detailed Logging:      " + isDetailedLoggingEnabled());
        System.out.println("===================================\n");
    }

    /**
     * Reload configuration (useful for testing)
     */
    public static void reload() {
        loadConfig();
    }
}
