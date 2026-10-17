package io.github.as9pa.packorganizer.gui;

import net.minecraft.client.gui.Gui;

/** Implemented by {@code ResourcePackListEntry} via mixin: how deep a row sits in the folder tree. */
public interface RowLayout {
    /** Pixels per level of indent. */
    int INDENT_STEP = 14;
    /** The row is 200 px wide, so the indent stops growing after this many levels. */
    int MAX_INDENT_LEVELS = 4;
    int GUIDE_COLOUR = 0x60FFFFFF;

    void packorganizer$setDepth(int depth);

    int packorganizer$depth();

    static int indentPx(int depth) {
        return Math.min(Math.max(depth, 0), MAX_INDENT_LEVELS) * INDENT_STEP;
    }

    /** One 1 px vertical guide per indent level, drawn left of the shifted row from the original x. */
    static void drawGuides(int x, int y, int slotHeight, int depth) {
        int levels = Math.min(Math.max(depth, 0), MAX_INDENT_LEVELS);
        for (int level = 0; level < levels; level++) {
            int gx = x + level * INDENT_STEP + INDENT_STEP / 2;
            // Rows are slotHeight + 4 apart; reach 2 px past each end so the guides join up.
            Gui.drawRect(gx, y - 2, gx + 1, y + slotHeight + 2, GUIDE_COLOUR);
        }
    }
}
