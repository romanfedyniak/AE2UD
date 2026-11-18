package appeng.util.item;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class OreDictFilterMatcherTest {

    private static boolean matches(final String expression, final String oreDict) {
        return OreDictFilterMatcher.matches(OreDictFilterMatcher.parseExpression(expression), oreDict);
    }

    @Test
    public void groupsParseAsBefore() {
        assertTrue(matches("ingot*", "ingotIron"));
        assertTrue(matches("(ingot*|dust*)&*Copper", "dustCopper"));
        assertFalse(matches("(ingot*|dust*)&*Copper", "ingotIron"));
        assertTrue(matches("((ingot*)|(gem*))&*Diamond", "gemDiamond"));
        assertFalse(matches("((ingot*)|(gem*))&*Diamond", "blockDiamond"));
        assertTrue(matches("(ore*", "oreGold"));
    }

    @Test
    public void refusesWhatTheScreenCannotSend() {
        final StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 30000; i++) {
            deep.append('(');
        }
        assertFalse(OreDictFilterMatcher.isAcceptable(deep.toString()));
        assertFalse(OreDictFilterMatcher.isAcceptable(deep.substring(0, OreDictFilterMatcher.MAX_DEPTH + 1)));
        assertTrue(OreDictFilterMatcher.isAcceptable(deep.substring(0, OreDictFilterMatcher.MAX_DEPTH)));
        assertTrue(OreDictFilterMatcher.isAcceptable("(ingot*|dust*)&*Copper"));
    }
}
