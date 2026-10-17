package io.github.as9pa.packorganizer.gui.preview;

import static org.junit.Assert.*;

import org.junit.Test;

public class PreviewFramebufferTest {
    @Test
    public void scaleOneFlipsYFromTheBottom() {
        // 854x480 window: a rect 10 down and 20 tall ends 30 down, so 450 up from the bottom.
        assertArrayEquals(new int[] {5, 450, 40, 20}, PreviewFramebuffer.pixelRect(5, 10, 40, 20, 1, 480));
    }

    @Test
    public void scaleMultipliesEverySide() {
        // Panel at scale 2 on 1920x1080 (GUI 960x540): x [688, 952], y [28, 489].
        int[] px = PreviewFramebuffer.pixelRect(688, 28, 264, 461, 2, 1080);
        assertArrayEquals(new int[] {1376, 102, 528, 922}, px);
    }

    @Test
    public void rectAtTheTopReachesTheWindowTop() {
        int[] px = PreviewFramebuffer.pixelRect(0, 0, 100, 60, 3, 720);
        assertEquals(720, px[1] + px[3]);
    }

    @Test
    public void rectAtTheBottomStartsAtPixelZero() {
        // GUI height 240 at scale 2 on a 480 window: a rect ending at 240 sits on row 0.
        int[] px = PreviewFramebuffer.pixelRect(10, 200, 50, 40, 2, 480);
        assertEquals(0, px[1]);
        assertEquals(80, px[3]);
    }

    @Test
    public void oddWindowHeightCanPushPastTheBottom() {
        // 481 px at scale 2 gives a GUI height of 240.5; the last GUI row ends half a unit below the window.
        int[] px = PreviewFramebuffer.pixelRect(0, 200, 10, 41, 2, 481);
        assertEquals(-1, px[1]);
    }
}
