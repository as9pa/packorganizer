package io.github.as9pa.packview.gui.preview;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpinSpringSettledTest {
    @Test
    public void settledAtRestNotWhileDraggedOrEasingBack() {
        SpinSpring spin = new SpinSpring();
        assertTrue(spin.settled());
        spin.press(0);
        assertFalse(spin.settled());
        spin.drag(120, 1_000_000L);
        spin.release(500_000_000L);
        assertFalse(spin.settled());
        spin.step(0.5f);
        assertFalse(spin.settled());
        spin.step(5f);
        assertTrue(spin.settled());
    }
}
