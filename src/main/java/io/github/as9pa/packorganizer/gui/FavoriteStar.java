package io.github.as9pa.packorganizer.gui;

/**
 * The 7x7 favorite star on a pack row's name line, just left of the resolution badge: gold with a text-style
 * shadow on favorites, a grey outline on the hovered row otherwise. A row showing its preview strip puts the star
 * under the badge instead, since the strip runs up to the badge. Pure geometry and hit test; the drawing and
 * the click live in MixinResourcePackListEntry. All x values are relative to the row's unindented x.
 */
public final class FavoriteStar {
    public static final int SIZE = 7;
    /** Space between the star's right edge and the badge's left edge. */
    public static final int GAP = 2;
    /** Extra clickable margin around the star on every side. */
    public static final int PAD = 2;
    /** Star top, relative to the row's y: the name line's text top. */
    public static final int TOP = 1;
    /** Star top on a strip row, relative to the row's y: the description line's text top, under the badge. */
    public static final int STRIP_TOP = 12;
    /** How much earlier the name is trimmed while the star shows. */
    public static final int NAME_ROOM = 10;

    public static final int GOLD = 0xFFFFD23C;
    /** Vanilla's text shadow rule: each channel / 4, offset 1 px down and right. */
    public static final int SHADOW = 0xFF000000 | (GOLD & 0xFCFCFC) >> 2;
    public static final int OUTLINE = 0xFFC8C8C8;

    private static final String[] PATTERN = {
            "...#...",
            "...#...",
            "#######",
            ".#####.",
            "..###..",
            ".##.##.",
            "#.....#",
    };

    private FavoriteStar() {}

    /** Whether (px, py) inside the 7x7 is part of the star. Outside the square is false. */
    public static boolean filled(int px, int py) {
        return px >= 0 && px < SIZE && py >= 0 && py < SIZE && PATTERN[py].charAt(px) == '#';
    }

    /** A star pixel with an empty (or out-of-square) neighbour above, below, left or right: drawn by the outline. */
    public static boolean edge(int px, int py) {
        return filled(px, py) && !(filled(px - 1, py) && filled(px + 1, py)
                && filled(px, py - 1) && filled(px, py + 1));
    }

    /**
     * Star left edge: GAP left of the badge when one is drawn (badgeWidth > 0), else flush with where the badge's
     * right edge would be.
     */
    public static int left(int badgeRight, int badgeWidth) {
        return badgeWidth > 0 ? badgeRight - badgeWidth - GAP - SIZE : badgeRight - SIZE;
    }

    /**
     * Star left edge on a row with or without the preview strip. Beside the badge the star would overlap the 16 px
     * strip (which ends at 162), so a strip row sets it flush right under the badge, at {@link #STRIP_TOP}.
     */
    public static int left(int badgeRight, int badgeWidth, boolean strip) {
        return left(badgeRight, strip ? 0 : badgeWidth);
    }

    /** Star top, relative to the row's y: {@link #STRIP_TOP} on a strip row, else {@link #TOP}. */
    public static int top(boolean strip) {
        return strip ? STRIP_TOP : TOP;
    }

    /** Whether a click at (relX, relY) in row coordinates lands on the star at starLeft, starTop, padding included. */
    public static boolean hit(int relX, int relY, int starLeft, int starTop) {
        return relX >= starLeft - PAD && relX < starLeft + SIZE + PAD
                && relY >= starTop - PAD && relY < starTop + SIZE + PAD;
    }
}
