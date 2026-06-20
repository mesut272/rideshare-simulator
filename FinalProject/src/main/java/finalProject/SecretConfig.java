package finalProject;

public final class SecretConfig {
    private SecretConfig() {
    }

    public static String deepSeekApiKey() {
        String propertyValue = System.getProperty("deepseek.api.key");
        if (propertyValue != null) {
            return requireNonBlank(propertyValue);
        }

        String environmentValue = System.getenv("DEEPSEEK_API_KEY");
        if (environmentValue != null) {
            return requireNonBlank(environmentValue);
        }

        throw new IllegalStateException("DeepSeek API key is required. Set DEEPSEEK_API_KEY or -Ddeepseek.api.key.");
    }

    private static String requireNonBlank(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException("DeepSeek API key is required. Set DEEPSEEK_API_KEY or -Ddeepseek.api.key.");
        }
        return trimmed;
    }
}
