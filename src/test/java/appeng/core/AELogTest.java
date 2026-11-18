package appeng.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 * L-08: most callers build the log message with data in it and pass no parameters, so a {@code %} in that data
 * must not be read as a format specifier; and the debug log is asked before the config exists. The message is
 * checked where AELog formats it, since the logger itself needs a running game to pick its side.
 */
class AELogTest {

    private static String format(final String message, final Object... params) throws Exception {
        final Method format = AELog.class.getDeclaredMethod("format", String.class, Object[].class);
        format.setAccessible(true);
        return (String) format.invoke(null, message, params);
    }

    @Test
    void percentInAMessageWithoutParametersIsLoggedAsIs() throws Exception {
        assertEquals("Invalid value 'a%b' for x", format("Invalid value 'a%b' for x"));
        assertEquals("100%", format("100%"));
        assertEquals("Conversion = '%'", format("Conversion = '%'"));
    }

    @Test
    void messageThatDoesNotFormatIsLoggedRaw() throws Exception {
        assertEquals("%d items [not a number]", format("%d items", "not a number"));
        assertEquals("%s and %s [only one]", format("%s and %s", "only one"));
        assertEquals("a 1 b", format("a %s b", 1));
    }

    @Test
    void debugBeforeTheConfigIsLoaded() {
        assertDoesNotThrow(() -> AELog.debug("before the config"));
    }
}
