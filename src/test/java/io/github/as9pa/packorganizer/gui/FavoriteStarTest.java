package io.github.as9pa.packorganizer.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FavoriteStarTest {
    @Test
    public void sitsTwoPixelsLeftOfTheBadge() {
        // Badge "16x" 17 px wide, right edge 188: badge left 171, star right edge 169, star left 162.
        assertEquals(162, FavoriteStar.left(188, 17));
        assertEquals(171 - FavoriteStar.GAP, FavoriteStar.left(188, 17) + FavoriteStar.SIZE);
    }

    @Test
    public void withoutABadgeSitsFlushRight() {
        assertEquals(188 - 7, FavoriteStar.left(188, 0));
    }

    @Test
    public void hitIncludesPadding() {
        int left = 162;
        // Top-left and bottom-right of the 7x7.
        assertTrue(FavoriteStar.hit(162, 1, left));
        assertTrue(FavoriteStar.hit(168, 7, left));
        // Padding: 2 px on every side.
        assertTrue(FavoriteStar.hit(160, -1, left));
        assertTrue(FavoriteStar.hit(170, 9, left));
        assertFalse(FavoriteStar.hit(159, 4, left));
        assertFalse(FavoriteStar.hit(171, 4, left));
        assertFalse(FavoriteStar.hit(165, -2, left));
        assertFalse(FavoriteStar.hit(165, 10, left));
    }

    @Test
    public void patternMatchesTheSpec() {
        String[] expected = {
                "...#...",
                "...#...",
                "#######",
                ".#####.",
                "..###..",
                ".##.##.",
                "#.....#",
        };
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                assertEquals("(" + x + "," + y + ")", expected[y].charAt(x) == '#', FavoriteStar.filled(x, y));
            }
        }
        assertFalse(FavoriteStar.filled(-1, 0));
        assertFalse(FavoriteStar.filled(7, 2));
    }

    @Test
    public void outlineDropsOnlyInteriorPixels() {
        // Interior: every 4-neighbour filled.
        assertFalse(FavoriteStar.edge(3, 2));
        assertFalse(FavoriteStar.edge(2, 3));
        assertFalse(FavoriteStar.edge(3, 3));
        assertFalse(FavoriteStar.edge(4, 3));
        // Edges, including the tips and the bottom of the body.
        assertTrue(FavoriteStar.edge(3, 0));
        assertTrue(FavoriteStar.edge(0, 2));
        assertTrue(FavoriteStar.edge(3, 4));
        assertTrue(FavoriteStar.edge(6, 6));
        // Empty pixels are never edge.
        assertFalse(FavoriteStar.edge(0, 0));
        assertFalse(FavoriteStar.edge(3, 5));
    }

    @Test
    public void shadowIsVanillaQuarter() {
        assertEquals(0xFF3F340F, FavoriteStar.SHADOW);
    }
}
