package io.github.as9pa.packview.gui;

/**
 * What a pack row draws right of its icon: the name and description, or the preview strip, and whether the
 * resolution label shows. Names mode (listPreviews off) swaps the hovered row to its strip and drops the label;
 * Previews mode shows the strip on every row but the hovered one and keeps the label wherever it fits beside the
 * strip ({@link #labelFits}).
 */
public enum RowLook {
    /** Name and description, label shown. */
    TEXT(false, true),
    /** Strip in place of the text, no label: the hovered row in Names mode. */
    STRIP(true, false),
    /** Strip in place of the text, label kept where it fits: every row but the hovered one in Previews mode. */
    STRIP_WITH_LABEL(true, true);

    /** Smallest gap, in GUI px, between the strip's right end and the label's left edge. */
    public static final int LABEL_GAP = 2;

    public final boolean strip;
    public final boolean label;

    RowLook(boolean strip, boolean label) {
        this.strip = strip;
        this.label = label;
    }

    /** previewReady is false for rows that never get a strip (folders, the Default row) and while one loads. */
    public static RowLook of(boolean listPreviews, boolean hovered, boolean previewReady) {
        if (!previewReady) {
            return TEXT;
        }
        if (listPreviews) {
            return hovered ? TEXT : STRIP_WITH_LABEL;
        }
        return hovered ? STRIP : TEXT;
    }

    /**
     * 1.8.9 GuiSlot calls drawEntry for every row, scrolled out of view or not, so Previews mode asks this before
     * requesting a preview: a row is on screen when any of it lies between the list's top and bottom.
     */
    public static boolean onScreen(int y, int slotHeight, int listTop, int listBottom) {
        return y < listBottom && y + slotHeight > listTop;
    }

    /**
     * Whether a right-aligned label still clears the strip on a {@link #STRIP_WITH_LABEL} row: its left edge
     * (labelRight - labelWidth) must sit at least {@link #LABEL_GAP} px right of stripRight. Indented rows and wide
     * labels fail this, and the row then drops its label.
     */
    public static boolean labelFits(int stripRight, int labelRight, int labelWidth) {
        return labelRight - labelWidth - stripRight >= LABEL_GAP;
    }
}
