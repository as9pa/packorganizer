package io.github.as9pa.packorganizer.gui;

/**
 * The 32x32 yellow folder drawn on folder rows, built from code so there is no asset to ship.
 * Pure: the texture upload lives in {@link FolderRowEntry} so tests can call {@link #pixels()}.
 */
public final class FolderIcon {
    public static final int SIZE = 32;

    static final int CLEAR = 0x00000000;
    static final int OUTLINE = 0xFF3A2A10;
    static final int BODY = 0xFFE8B93B;
    static final int TOP_BAND = 0xFFF6D36A;
    static final int BOTTOM_SHADE = 0xFFB5862A;
    static final int TAB = 0xFFC99A2E;

    // Tab: outline box x 3..13, y 5..9 (its bottom edge is the body's top edge).
    static final int TAB_LEFT = 3, TAB_RIGHT = 13, TAB_TOP = 5;
    // Body: outline box x 2..29, y 9..27.
    static final int BODY_LEFT = 2, BODY_RIGHT = 29, BODY_TOP = 9, BODY_BOTTOM = 27;
    static final int BAND_ROWS = 3;

    private FolderIcon() {}

    /** ARGB pixels, row-major, SIZE * SIZE long. Returns a new array each call. */
    public static int[] pixels() {
        int[] p = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                p[y * SIZE + x] = colourAt(x, y);
            }
        }
        return p;
    }

    private static int colourAt(int x, int y) {
        if (x >= BODY_LEFT && x <= BODY_RIGHT && y >= BODY_TOP && y <= BODY_BOTTOM) {
            if (x == BODY_LEFT || x == BODY_RIGHT || y == BODY_TOP || y == BODY_BOTTOM) {
                return OUTLINE;
            }
            if (y < BODY_TOP + 1 + BAND_ROWS) {
                return TOP_BAND;
            }
            if (y > BODY_BOTTOM - 1 - BAND_ROWS) {
                return BOTTOM_SHADE;
            }
            return BODY;
        }
        if (x >= TAB_LEFT && x <= TAB_RIGHT && y >= TAB_TOP && y < BODY_TOP) {
            // Cut the tab's top-right corner so it reads as a tab, not a box.
            if (y == TAB_TOP && x == TAB_RIGHT) {
                return CLEAR;
            }
            if (x == TAB_LEFT || x == TAB_RIGHT || y == TAB_TOP || (y == TAB_TOP + 1 && x == TAB_RIGHT - 1)) {
                return OUTLINE;
            }
            return TAB;
        }
        return CLEAR;
    }
}
