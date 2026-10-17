package io.github.as9pa.packorganizer.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.as9pa.packorganizer.gui.ColorWheelPanel.Geometry;
import org.junit.Test;

public class ColorWheelGeometryTest {
    private static int at(int[] p, int x, int y) {
        return p[y * Geometry.SIZE + x];
    }

    @Test
    public void offsetsAreSymmetricAboutTheCentre() {
        assertEquals(-Geometry.toOffset(0), Geometry.toOffset(Geometry.SIZE - 1), 1e-9);
        assertEquals(-Geometry.toOffset(55), Geometry.toOffset(56), 1e-9);
        assertTrue(Geometry.toOffset(0) > -1);
    }

    @Test
    public void toPixelInvertsToOffset() {
        for (int p = 0; p < Geometry.SIZE; p++) {
            assertEquals(p, Geometry.toPixel(Geometry.toOffset(p)));
        }
        assertEquals(0, Geometry.toPixel(-5));
        assertEquals(Geometry.SIZE - 1, Geometry.toPixel(5));
    }

    @Test
    public void clampKeepsInsideAndPullsOutsideToTheRim() {
        double[] inside = Geometry.clampToWheel(0.3, -0.4);
        assertEquals(0.3, inside[0], 1e-9);
        assertEquals(-0.4, inside[1], 1e-9);
        double[] outside = Geometry.clampToWheel(3, 4);
        double r = Math.sqrt(outside[0] * outside[0] + outside[1] * outside[1]);
        assertTrue(r < 1 && r > 0.99);
        assertEquals(0.75, outside[0] / outside[1], 1e-9);
    }

    @Test
    public void wheelPixels() {
        int[] p = Geometry.wheelPixels();
        assertEquals(Geometry.SIZE * Geometry.SIZE, p.length);
        // Corners are outside the circle.
        assertEquals(0, at(p, 0, 0) >>> 24);
        assertEquals(0, at(p, 111, 111) >>> 24);
        // Centre is near white.
        int centre = at(p, 56, 56);
        assertEquals(0xFF, centre >>> 24);
        assertTrue(((centre >> 8) & 0xFF) > 0xF0);
        // Mid-radius straight up is pure red; to the right (90 degrees) is yellow-green.
        int top = at(p, 56, 20);
        assertEquals(0xFFFF, top >>> 16);
        assertTrue(((top >> 8) & 0xFF) < 0x10 && (top & 0xFF) < 0x10);
        int right = at(p, 92, 56);
        assertTrue(((right >> 16) & 0xFF) > 0x70 && ((right >> 8) & 0xFF) > 0xF0 && (right & 0xFF) < 0x10);
        // The rim is dark.
        int rim = at(p, 56, 1);
        assertTrue(((rim >> 16) & 0xFF) < 0x30);
    }
}
