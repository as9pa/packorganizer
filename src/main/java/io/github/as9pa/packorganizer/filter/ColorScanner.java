package io.github.as9pa.packorganizer.filter;

import io.github.as9pa.packorganizer.Log;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Reads a pack's diamond sword and diamond armor textures and turns them into {@link ColorFeatures}.
 * Runs on a background thread; must not touch Minecraft classes.
 */
public final class ColorScanner {
    private static final String ITEMS = "assets/minecraft/textures/items/";
    private static final String ARMOR = "assets/minecraft/textures/models/armor/";

    static final String SWORD = ITEMS + "diamond_sword.png";
    /** Preferred armor source: both worn layers, pixels pooled. */
    static final String[] ARMOR_LAYERS = {ARMOR + "diamond_layer_1.png", ARMOR + "diamond_layer_2.png"};
    /** Fallback armor source when the pack has neither layer: the four inventory icons, pixels pooled. */
    static final String[] ARMOR_ITEMS = {
        ITEMS + "diamond_helmet.png", ITEMS + "diamond_chestplate.png", ITEMS + "diamond_leggings.png", ITEMS + "diamond_boots.png",
    };

    /** A texture's ARGB pixels inside one pack, or null when it is missing or unreadable. */
    private interface Textures {
        int[] pixels(String path);
    }

    private ColorScanner() {}

    /** Never throws. {@link PackColors#NONE} when the pack cannot be opened. */
    public static PackColors scan(final File pack) {
        try {
            if (pack.isDirectory()) {
                return scan(path -> {
                    File f = new File(pack, path);
                    if (!f.isFile()) {
                        return null;
                    }
                    try (InputStream in = new FileInputStream(f)) {
                        return decode(in);
                    } catch (IOException e) {
                        return null;
                    }
                });
            }
            if (!pack.isFile()) {
                return PackColors.NONE;
            }
            try (ZipFile zip = new ZipFile(pack)) {
                return scan(path -> {
                    ZipEntry entry = zip.getEntry(path);
                    if (entry == null) {
                        return null;
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        return decode(in);
                    } catch (IOException e) {
                        return null;
                    }
                });
            }
        } catch (IOException | RuntimeException e) {
            Log.LOG.debug("packorganizer: could not read colors of {}: {}", pack.getName(), e.toString());
            return PackColors.NONE;
        }
    }

    private static PackColors scan(Textures pack) {
        int[] sword = pack.pixels(SWORD);
        int[] armor = pooled(pack, ARMOR_LAYERS);
        if (armor == null) {
            armor = pooled(pack, ARMOR_ITEMS);
        }
        return new PackColors(sword == null ? null : ColorFeatures.from(sword), armor == null ? null : ColorFeatures.from(armor));
    }

    /** The pixels of every present texture in {@code paths}, concatenated; null when none is present. */
    private static int[] pooled(Textures pack, String[] paths) {
        List<int[]> found = new ArrayList<>();
        int total = 0;
        for (String path : paths) {
            int[] px = pack.pixels(path);
            if (px != null) {
                found.add(px);
                total += px.length;
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        int[] out = new int[total];
        int at = 0;
        for (int[] px : found) {
            System.arraycopy(px, 0, out, at, px.length);
            at += px.length;
        }
        return out;
    }

    /** ARGB pixels of the image, cropped to its top square when taller than wide (animation strips). */
    private static int[] decode(InputStream in) {
        BufferedImage img;
        try {
            img = ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
            return null;
        }
        int w = img.getWidth();
        int h = Math.min(img.getHeight(), w);
        return img.getRGB(0, 0, w, h, null, 0, w);
    }
}
