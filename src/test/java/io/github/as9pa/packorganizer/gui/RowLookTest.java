package io.github.as9pa.packorganizer.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RowLookTest {
    // Names mode: today's behavior, the strip only on the hovered row once it is ready.

    @Test
    public void namesIdleNotReadyShowsText() {
        assertEquals(RowLook.TEXT, RowLook.of(false, false, false));
    }

    @Test
    public void namesIdleReadyShowsText() {
        assertEquals(RowLook.TEXT, RowLook.of(false, false, true));
    }

    @Test
    public void namesHoveredLoadingShowsText() {
        assertEquals(RowLook.TEXT, RowLook.of(false, true, false));
    }

    @Test
    public void namesHoveredReadyShowsStripWithoutLabel() {
        assertEquals(RowLook.STRIP, RowLook.of(false, true, true));
    }

    // Previews mode: the strip on every ready row but the hovered one, the label on every row.

    @Test
    public void previewsIdleLoadingShowsText() {
        assertEquals(RowLook.TEXT, RowLook.of(true, false, false));
    }

    @Test
    public void previewsIdleReadyShowsStripWithLabel() {
        assertEquals(RowLook.STRIP_WITH_LABEL, RowLook.of(true, false, true));
    }

    @Test
    public void previewsHoveredLoadingShowsText() {
        assertEquals(RowLook.TEXT, RowLook.of(true, true, false));
    }

    @Test
    public void previewsHoveredReadySwapsBackToText() {
        assertEquals(RowLook.TEXT, RowLook.of(true, true, true));
    }

    @Test
    public void lookFlags() {
        assertFalse(RowLook.TEXT.strip);
        assertTrue(RowLook.TEXT.label);
        assertTrue(RowLook.STRIP.strip);
        assertFalse(RowLook.STRIP.label);
        assertTrue(RowLook.STRIP_WITH_LABEL.strip);
        assertTrue(RowLook.STRIP_WITH_LABEL.label);
    }

    @Test
    public void labelHiddenOnlyForNamesHoveredReady() {
        for (int bits = 0; bits < 8; bits++) {
            boolean listPreviews = (bits & 4) != 0;
            boolean hovered = (bits & 2) != 0;
            boolean ready = (bits & 1) != 0;
            boolean expected = listPreviews || !hovered || !ready;
            assertEquals("bits " + bits, expected, RowLook.of(listPreviews, hovered, ready).label);
        }
    }

    // Rows are 32 px tall here; the list spans y 52..189.

    @Test
    public void rowInsideListIsOnScreen() {
        assertTrue(RowLook.onScreen(100, 32, 52, 189));
    }

    @Test
    public void rowsStraddlingEitherEdgeAreOnScreen() {
        assertTrue(RowLook.onScreen(30, 32, 52, 189));
        assertTrue(RowLook.onScreen(180, 32, 52, 189));
    }

    @Test
    public void rowsFullyOutsideAreOffScreen() {
        assertFalse(RowLook.onScreen(20, 32, 52, 189));
        assertFalse(RowLook.onScreen(189, 32, 52, 189));
        assertFalse(RowLook.onScreen(-500, 32, 52, 189));
        assertFalse(RowLook.onScreen(900, 32, 52, 189));
    }
}
