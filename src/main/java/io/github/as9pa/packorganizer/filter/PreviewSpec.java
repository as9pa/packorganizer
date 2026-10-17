package io.github.as9pa.packorganizer.filter;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** What the hover preview shows: 16 pack textures laid out as a 2x8 atlas. No Minecraft classes. */
public final class PreviewSpec {
    private static final String ROOT = "assets/minecraft/textures/";
    /** Source of the heart cell; cropped with {@link #heartCrop}. */
    public static final String ICONS = ROOT + "gui/icons.png";
    public static final int CELLS = 16;
    /** Index of the heart cell in {@link #PATHS}. */
    public static final int HEART = 12;
    public static final int COLUMNS = 8;
    public static final int ROWS = 2;
    public static final int MAX_CELL = 64;
    /** Cell size when no texture loaded. */
    public static final int DEFAULT_CELL = 16;

    /** Zip entry paths in atlas order, row by row. */
    public static final List<String> PATHS = Collections.unmodifiableList(Arrays.asList(
            ROOT + "blocks/stone.png",
            ROOT + "blocks/planks_oak.png",
            ROOT + "blocks/cobblestone.png",
            ROOT + "blocks/wool_colored_white.png",
            ROOT + "items/apple_golden.png",
            ROOT + "items/ender_pearl.png",
            ROOT + "items/fishing_rod_uncast.png",
            ROOT + "items/bow_standby.png",
            ROOT + "items/diamond_sword.png",
            ROOT + "items/diamond_pickaxe.png",
            ROOT + "items/diamond_chestplate.png",
            ROOT + "items/potion_bottle_splash.png",
            ICONS,
            ROOT + "items/diamond.png",
            ROOT + "items/arrow.png",
            ROOT + "items/snowball.png"));

    private PreviewSpec() {}

    /** Heart rectangle {x, y, w, h} in an icons.png of the given width: (52,0,9,9) scaled by width/256. */
    public static int[] heartCrop(int iconsWidth) {
        return new int[] {52 * iconsWidth / 256, 0, 9 * iconsWidth / 256, 9 * iconsWidth / 256};
    }

    /** The heart cut from icons.png, or null if the image is too small to hold it. */
    public static BufferedImage cropHeart(BufferedImage icons) {
        int[] r = heartCrop(icons.getWidth());
        if (r[2] <= 0 || r[3] <= 0 || r[0] + r[2] > icons.getWidth() || r[1] + r[3] > icons.getHeight()) {
            return null;
        }
        return icons.getSubimage(r[0], r[1], r[2], r[3]);
    }

    /** Top square frame of a vertical animation strip; square or wide images are returned as is. */
    public static BufferedImage firstFrame(BufferedImage img) {
        int w = img.getWidth();
        return img.getHeight() > w ? img.getSubimage(0, 0, w, w) : img;
    }

    /** Atlas cell size: the largest side among loaded textures, capped at {@link #MAX_CELL}. Nulls are skipped. */
    public static int cellSize(List<BufferedImage> textures) {
        int max = 0;
        for (BufferedImage t : textures) {
            if (t != null) {
                max = Math.max(max, Math.max(t.getWidth(), t.getHeight()));
            }
        }
        return max == 0 ? DEFAULT_CELL : Math.min(max, MAX_CELL);
    }

    public static int atlasWidth(int cell) {
        return COLUMNS * cell;
    }

    public static int atlasHeight(int cell) {
        return ROWS * cell;
    }

    public static int cellX(int index, int cell) {
        return (index % COLUMNS) * cell;
    }

    public static int cellY(int index, int cell) {
        return (index / COLUMNS) * cell;
    }
}
