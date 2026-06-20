package finalProject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretConfigTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("deepseek.api.key");
    }

    @Test
    void deepSeekApiKeyCanComeFromSystemProperty() {
        System.setProperty("deepseek.api.key", "test-key");

        assertEquals("test-key", SecretConfig.deepSeekApiKey());
    }

    @Test
    void deepSeekApiKeyRejectsBlankSystemProperty() {
        System.setProperty("deepseek.api.key", "   ");

        IllegalStateException error = assertThrows(IllegalStateException.class, SecretConfig::deepSeekApiKey);

        assertTrue(error.getMessage().contains("DEEPSEEK_API_KEY"));
    }
}
