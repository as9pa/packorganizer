package io.github.as9pa.packview.gui.preview;

import static org.junit.Assert.*;

import org.junit.Test;

public class MotionTest {
    @Test
    public void headCentredLooksStraight() {
        HeadFollow h = new HeadFollow();
        h.target(0, 0);
        h.snap();
        assertEquals(0, h.headYaw(), 1e-4);
        assertEquals(0, h.pitch(), 1e-4);
    }

    @Test
    public void pitchStaysUnder42Degrees() {
        HeadFollow h = new HeadFollow();
        h.target(0, -1e6f);
        h.snap();
        assertTrue(h.pitch() > 41 && h.pitch() < 42);
    }

    @Test
    public void cursorOneBlockBelowIsAboutEighteenDegrees() {
        HeadFollow h = new HeadFollow();
        h.target(0, -30);
        h.snap();
        // my = -30/1.3, atan(my/40) = -0.5236 rad, x35
        assertEquals(18.33, h.pitch(), 0.05);
    }

    @Test
    public void bodyFollowsHalfTheHead() {
        HeadFollow h = new HeadFollow();
        h.target(80, 0);
        h.snap();
        assertEquals(h.headYaw() / 2, h.bodyYaw(), 1e-4);
    }

    @Test
    public void easingReaches63PercentInATenth() {
        HeadFollow ref = new HeadFollow();
        ref.target(80, 0);
        ref.snap();
        float full = ref.headYaw();
        HeadFollow h = new HeadFollow();
        h.target(80, 0);
        h.step(0.1f);
        assertEquals(full * 0.632, h.headYaw(), full * 0.01);
    }

    @Test
    public void springSettlesWithoutOvershootFromRest() {
        SpinSpring s = new SpinSpring();
        s.press(0);
        s.drag(120, 0);
        s.release(200_000_000L);
        float min = 999;
        for (int i = 0; i < 120; i++) {
            s.step(1 / 60f);
            min = Math.min(min, s.angle());
        }
        // The exact curve leaves 0.88 deg after 2 s; the plan's semi-implicit steps at 60 fps leave about 1.06.
        assertTrue("angle " + s.angle(), Math.abs(s.angle()) < 1.1);
        assertTrue("min " + min, min > -0.5f);
    }

    @Test
    public void springTakesShortWayRound() {
        SpinSpring s = new SpinSpring();
        s.press(0);
        s.drag(300, 0);
        s.release(500_000_000L);
        assertEquals(-60, s.angle(), 1e-3);
    }

    @Test
    public void flickKeepsVelocity() {
        SpinSpring s = new SpinSpring();
        s.press(0);
        for (int i = 1; i <= 5; i++) s.drag(10, i * 16_000_000L);
        s.release(5 * 16_000_000L + 10_000_000L);
        s.step(0.016f);
        assertTrue(s.angle() > 50);
    }

    @Test
    public void swingLastsSixTicks() {
        SwingTimer t = new SwingTimer();
        t.swing();
        for (int i = 0; i < 6; i++) t.tick();
        assertEquals(0, t.progress(0), 1e-6);
    }

    @Test
    public void swingRestartsOnlyPastHalf() {
        SwingTimer t = new SwingTimer();
        t.swing();
        t.tick();
        float p = t.progress(0);
        t.swing();
        assertEquals(p, t.progress(0), 1e-6);
        t.tick();
        t.tick();
        t.swing();
        assertEquals(0, t.progress(0), 1e-6);
    }
}
