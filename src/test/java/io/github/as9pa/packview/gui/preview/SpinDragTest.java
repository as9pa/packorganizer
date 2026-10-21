package io.github.as9pa.packview.gui.preview;

import static org.junit.Assert.*;

import org.junit.Test;

/** The per-frame drag: window pixels to degrees, and the fling speed from frame samples. */
public class SpinDragTest {
    /** A step this short barely moves the spring, so the angle it adds over it is the release speed. */
    private static final float H = 0.001f;

    @Test
    public void rightTurnsPositiveAtTheGuiScale() {
        // GUI scale 2: 960 GUI px across 1920 window px, so 10 window px are 5 GUI px at 1.5 deg each.
        assertEquals(7.5f, SpinSpring.dragDegrees(100, 110, 960, 1920, 1.5f), 1e-5);
        assertEquals(-7.5f, SpinSpring.dragDegrees(110, 100, 960, 1920, 1.5f), 1e-5);
        assertEquals(15f, SpinSpring.dragDegrees(0, 10, 854, 854, 1.5f), 1e-5);
        assertEquals(0f, SpinSpring.dragDegrees(42, 42, 960, 1920, 1.5f), 0);
    }

    @Test
    public void zeroDisplayWidthDoesNotBlowUp() {
        assertEquals(15f, SpinSpring.dragDegrees(0, 10, 1, 0, 1.5f), 1e-5);
    }

    @Test
    public void frameByFrameAddsUpToTheWholeMove() {
        SpinSpring s = new SpinSpring();
        s.press(0);
        int last = 500;
        // 1333 window px/s read at 144 fps: some frames move 9 px, some 10.
        for (int f = 1; f <= 72; f++) {
            int x = 500 + (int) (1333.3 * f / 144);
            s.drag(SpinSpring.dragDegrees(last, x, 960, 1920, 1.5f), f * 1_000_000_000L / 144);
            last = x;
        }
        assertEquals(SpinSpring.dragDegrees(500, last, 960, 1920, 1.5f), s.angle(), 1e-3);
    }

    @Test
    public void steadyDragFlingsAtItsSpeedWhateverTheFrameRate() {
        for (int fps : new int[] {30, 60, 144, 240, 1000}) {
            SpinSpring s = new SpinSpring();
            s.press(0);
            long t = dragAt(s, 0, 300, fps, 0.25f);
            assertEquals("fps " + fps, 300, releaseSpeed(s, t), 300 * 0.03);
        }
    }

    @Test
    public void lumpyMouseReportsStillAverageOut() {
        // A 125 Hz mouse read at 240 fps: about every other frame sees a report, the rest see no move.
        SpinSpring s = new SpinSpring();
        s.press(0);
        long frame = 1_000_000_000L / 240;
        long report = 8_000_000L;
        long t = 0;
        float lastPos = 0;
        for (int f = 1; f <= 60; f++) {
            t = f * frame;
            float pos = (t / report) * report / 1e9f * 300;   // degrees as of the latest report
            s.drag(pos - lastPos, t);
            lastPos = pos;
        }
        assertEquals(300, releaseSpeed(s, t), 300 * 0.15);
    }

    @Test
    public void flingFollowsTheMotionJustBeforeRelease() {
        SpinSpring s = new SpinSpring();
        s.press(0);
        long t = dragAt(s, 0, 600, 144, 0.3f);
        t = dragAt(s, t, 60, 144, 0.15f);
        float v = releaseSpeed(s, t);
        assertTrue("speed " + v, v > 50 && v < 90);
    }

    @Test
    public void holdingStillBeforeReleaseLeavesNoFling() {
        SpinSpring s = new SpinSpring();
        s.press(0);
        long t = dragAt(s, 0, 600, 144, 0.2f);
        t = dragAt(s, t, 0, 144, 0.1f);
        assertEquals(0, releaseSpeed(s, t), 5);
    }

    @Test
    public void pressDropsTheSpringsReturnSpeed() {
        SpinSpring s = new SpinSpring();
        s.press(0);
        dragAt(s, 0, 600, 60, 0.2f);
        s.release(200_000_000L);   // flung at 600 deg/s
        s.step(0.05f);
        s.press(300_000_000L);
        long t = dragAt(s, 300_000_000L, 0, 60, 0.05f);
        // Held without moving since the new press: no fling, though the spring was turning fast at the press.
        assertEquals(0, releaseSpeed(s, t), 5);
    }

    /** Drags at {@code degPerSecond} for {@code seconds} of frames from {@code start}; returns the last frame's time. */
    private static long dragAt(SpinSpring s, long start, float degPerSecond, int fps, float seconds) {
        long frame = 1_000_000_000L / fps;
        int frames = Math.round(seconds * fps);
        long t = start;
        for (int f = 0; f < frames; f++) {
            t += frame;
            s.drag(degPerSecond * frame / 1e9f, t);
        }
        return t;
    }

    private static float releaseSpeed(SpinSpring s, long nanoTime) {
        s.release(nanoTime);
        float before = s.angle();
        s.step(H);
        return (s.angle() - before) / H;
    }
}
