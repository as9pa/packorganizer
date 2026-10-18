package io.github.as9pa.packorganizer.gui;

/**
 * What a pack row draws right of its icon: the name and description, or the preview strip, and whether the
 * resolution label shows. Names mode (listPreviews off) swaps the hovered row to its strip and drops the label;
 * Previews mode shows the strip on every row but the hovered one and keeps the label everywhere.
 */
public enum RowLook {
    /** Name and description, label shown. */
    TEXT(false, true),
    /** Strip in place of the text, no label: the hovered row in Names mode. */
    STRIP(true, false),
    /** Strip in place of the text, label kept: every row but the hovered one in Previews mode. */
    STRIP_WITH_LABEL(true, true);

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
}
