package appeng.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * L-08: most callers build the log message with data in it and pass no parameters, so a {@code %} in that data
 * must not be read as a format specifier; and the debug log is asked before the config exists.
 */
class AELogTest {

    @Test
    void percentInAMessageWithoutParametersIsLoggedAsIs() {
        assertDoesNotThrow(() -> AELog.info("Invalid value 'a%b' for x"));
        assertDoesNotThrow(() -> AELog.warn("100%"));
        assertDoesNotThrow(() -> AELog.error(new IllegalStateException("x"), "Conversion = '%'"));
    }

    @Test
    void messageThatDoesNotFormatIsLoggedRaw() {
        assertDoesNotThrow(() -> AELog.info("%d items", "not a number"));
        assertDoesNotThrow(() -> AELog.info("%s and %s", "only one"));
    }

    @Test
    void debugBeforeTheConfigIsLoaded() {
        assertDoesNotThrow(() -> AELog.debug("before the config"));
    }
}
