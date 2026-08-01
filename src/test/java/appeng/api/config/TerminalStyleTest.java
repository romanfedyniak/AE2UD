package appeng.api.config;


import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class TerminalStyleTest {

    @Test
    public void scalesAvailableRowsByQuarter() {
        assertThat(TerminalStyle.SMALL.getRows(20), is(5));
        assertThat(TerminalStyle.MEDIUM.getRows(20), is(10));
        assertThat(TerminalStyle.TALL.getRows(20), is(15));
        assertThat(TerminalStyle.FULL.getRows(20), is(20));
    }

    @Test
    public void doesNotOverflowForLargeRowCounts() {
        assertThat(TerminalStyle.FULL.getRows(Integer.MAX_VALUE), is(Integer.MAX_VALUE));
    }
}
