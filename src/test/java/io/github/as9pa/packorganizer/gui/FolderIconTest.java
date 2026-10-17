package io.github.as9pa.packorganizer.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

public class FolderIconTest {
    private static int at(int[] p, int x, int y) {
        return p[y * FolderIcon.SIZE + x];
    }

    @Test
    public void isThirtyTwoSquare() {
        assertEquals(32 * 32, FolderIcon.pixels().length);
    }

    @Test
    public void cornersAreTransparent() {
        int[] p = FolderIcon.pixels();
        assertEquals(0, at(p, 0, 0) >>> 24);
        assertEquals(0, at(p, 31, 0) >>> 24);
        assertEquals(0, at(p, 0, 31) >>> 24);
        assertEquals(0, at(p, 31, 31) >>> 24);
        // Right of the tab, above the body.
        assertEquals(0, at(p, 20, 6) >>> 24);
    }

    @Test
    public void bodyColours() {
        int[] p = FolderIcon.pixels();
        assertEquals(0xFFE8B93B, at(p, 16, 18));
        assertEquals(0xFFF6D36A, at(p, 16, 10));
        assertEquals(0xFFF6D36A, at(p, 16, 12));
        assertEquals(0xFFB5862A, at(p, 16, 24));
        assertEquals(0xFFB5862A, at(p, 16, 26));
        assertEquals(0xFFC99A2E, at(p, 7, 7));
    }

    @Test
    public void outlineIsDarkAndOpaque() {
        int[] p = FolderIcon.pixels();
        int[] edge = {at(p, 2, 18), at(p, 29, 18), at(p, 16, 9), at(p, 16, 27), at(p, 3, 6), at(p, 8, 5)};
        for (int c : edge) {
            assertEquals(0xFF3A2A10, c);
        }
    }

    @Test
    public void everyPixelIsOpaqueOrClear() {
        for (int c : FolderIcon.pixels()) {
            int alpha = c >>> 24;
            assertEquals(true, alpha == 0 || alpha == 0xFF);
        }
    }

    @Test
    public void returnsAFreshArray() {
        assertNotSame(FolderIcon.pixels(), FolderIcon.pixels());
    }
}
