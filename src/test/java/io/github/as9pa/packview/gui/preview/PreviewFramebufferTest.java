package io.github.as9pa.packview.gui.preview;

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

    @Test
    public void supersamplesWhenTwiceTheWindowFits() {
        // The 1080p panel above: 3840x2160 fits a 16384 renderbuffer easily.
        assertEquals(2, PreviewFramebuffer.factor(528, 922, 1920, 1080, 16384));
    }

    @Test
    public void exactlyTheLimitStillSupersamples() {
        assertEquals(2, PreviewFramebuffer.factor(4096, 900, 4096, 2160, 8192));
    }

    @Test
    public void doubledRectPastTheLimitFallsBackToOne() {
        assertEquals(1, PreviewFramebuffer.factor(4097, 900, 4097, 2160, 8192));
        assertEquals(1, PreviewFramebuffer.factor(900, 4097, 3840, 4097, 8192));
    }

    @Test
    public void doubledWindowPastTheLimitFallsBackEvenForASmallRect() {
        // The viewport spans the whole window, so a wide window alone is enough.
        assertEquals(1, PreviewFramebuffer.factor(300, 400, 5000, 2000, 8192));
        assertEquals(1, PreviewFramebuffer.factor(300, 400, 2000, 5000, 8192));
    }

    @Test
    public void rectTallerThanTheWindowCountsItsOwnHeight() {
        // An odd window height can leave the rect a row taller than the window.
        assertEquals(2, PreviewFramebuffer.factor(10, 481, 480, 480, 962));
        assertEquals(1, PreviewFramebuffer.factor(10, 481, 480, 480, 961));
    }

    @Test
    public void hugeSizesDoNotOverflowIntoSupersampling() {
        assertEquals(1, PreviewFramebuffer.factor(Integer.MAX_VALUE, 1, 1, 1, Integer.MAX_VALUE));
    }
}
