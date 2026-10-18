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

    // Label fit: the label's right edge is row x + 188; a top-level strip ends at x + 162 (16 px cells).

    @Test
    public void labelFitsWithExactlyTheGap() {
        // 188 - 24 = 164, two px past 162.
        assertTrue(RowLook.labelFits(162, 188, 24));
    }

    @Test
    public void labelOneShortOfTheGapDoesNotFit() {
        assertFalse(RowLook.labelFits(162, 188, 25));
        assertFalse(RowLook.labelFits(163, 188, 24));
    }

    @Test
    public void labelTouchingOrOverlappingTheStripDoesNotFit() {
        assertFalse(RowLook.labelFits(164, 188, 24));
        assertFalse(RowLook.labelFits(176, 188, 18));
    }

    @Test
    public void narrowLabelFitsWithRoomToSpare() {
        assertTrue(RowLook.labelFits(162, 188, 18));
    }

    @Test
    public void labelFitIsRelativeToTheRow() {
        assertTrue(RowLook.labelFits(162 + 300, 188 + 300, 24));
        assertFalse(RowLook.labelFits(162 + 300, 188 + 300, 25));
    }
}
