package appeng.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * L-86: the translation keys of the permissions were built with the default locale's lower case, so on a Turkish
 * system INJECT got a dotless i. The enum is loaded afresh under a Turkish default locale.
 */
class SecurityPermissionsLocaleTest {

    @Test
    void keysDoNotDependOnTheDefaultLocale() throws Exception {
        final Locale old = Locale.getDefault();
        final URL source = SecurityPermissions.class.getProtectionDomain().getCodeSource().getLocation();
        Locale.setDefault(new Locale("tr", "TR"));
        try (URLClassLoader loader = new URLClassLoader(new URL[] { source }, null)) {
            final Class<?> fresh = Class.forName(SecurityPermissions.class.getName(), true, loader);
            for (final String name : new String[] { "INJECT", "EXTRACT", "CRAFT", "BUILD", "SECURITY" }) {
                final Object permission = fresh.getField(name).get(null);
                assertEquals("gui.appliedenergistics2.security." + name.toLowerCase(Locale.ROOT) + ".name",
                        fresh.getMethod("getUnlocalizedName").invoke(permission));
            }
        } finally {
            Locale.setDefault(old);
        }
    }
}
