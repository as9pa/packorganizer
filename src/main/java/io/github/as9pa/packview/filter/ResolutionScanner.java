package io.github.as9pa.packview.filter;

import io.github.as9pa.packview.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Works out a pack's resolution: an overlay when its name says so, else the one its name or pack.mcmeta description states, else by reading
 * the PNG width of a handful of PvP-relevant textures.
 * Runs on a background thread; must not touch Minecraft classes.
 */
public final class ResolutionScanner {
    private static final String ITEMS = "assets/minecraft/textures/items/";
    private static final String BLOCKS = "assets/minecraft/textures/blocks/";
    private static final String ARMOR = "assets/minecraft/textures/models/armor/";
    private static final String MCMETA = "pack.mcmeta";

    /** One vote each, at the PNG's width. */
    static final String[] SAMPLES = {
        ITEMS + "diamond_sword.png", ITEMS + "iron_sword.png", ITEMS + "bow_standby.png",
        ITEMS + "fishing_rod_uncast.png", ITEMS + "apple_golden.png", ITEMS + "ender_pearl.png",
        BLOCKS + "stone.png", BLOCKS + "planks_oak.png", BLOCKS + "sandstone_normal.png",
        BLOCKS + "dirt.png", BLOCKS + "cobblestone.png",
        ITEMS + "wood_sword.png", ITEMS + "stone_sword.png", ITEMS + "gold_sword.png",
        ITEMS + "leather_helmet.png", ITEMS + "leather_chestplate.png", ITEMS + "leather_leggings.png", ITEMS + "leather_boots.png",
        ITEMS + "chainmail_helmet.png", ITEMS + "chainmail_chestplate.png", ITEMS + "chainmail_leggings.png", ITEMS + "chainmail_boots.png",
        ITEMS + "iron_helmet.png", ITEMS + "iron_chestplate.png", ITEMS + "iron_leggings.png", ITEMS + "iron_boots.png",
        ITEMS + "gold_helmet.png", ITEMS + "gold_chestplate.png", ITEMS + "gold_leggings.png", ITEMS + "gold_boots.png",
        ITEMS + "diamond_helmet.png", ITEMS + "diamond_chestplate.png", ITEMS + "diamond_leggings.png", ITEMS + "diamond_boots.png",
        ITEMS + "potion_bottle_splash.png",
        ITEMS + "potion_bottle_drinkable.png", ITEMS + "fishing_rod_cast.png", ITEMS + "arrow.png", ITEMS + "fireball.png",
        ITEMS + "egg.png", ITEMS + "shears.png", ITEMS + "iron_ingot.png", ITEMS + "gold_ingot.png",
        ITEMS + "diamond.png", ITEMS + "emerald.png", ITEMS + "beef_cooked.png", ITEMS + "bucket_water.png",
        ITEMS + "bucket_lava.png", ITEMS + "snowball.png", ITEMS + "diamond_pickaxe.png", ITEMS + "iron_pickaxe.png",
        ITEMS + "diamond_axe.png", ITEMS + "iron_axe.png",
    };

    /** One vote each; 64 px wide at 16x, so the width is divided by 4. */
    static final String[] ARMOR_LAYERS = {
        ARMOR + "leather_layer_1.png", ARMOR + "leather_layer_2.png",
        ARMOR + "chainmail_layer_1.png", ARMOR + "chainmail_layer_2.png",
        ARMOR + "iron_layer_1.png", ARMOR + "iron_layer_2.png",
        ARMOR + "gold_layer_1.png", ARMOR + "gold_layer_2.png",
        ARMOR + "diamond_layer_1.png", ARMOR + "diamond_layer_2.png",
    };

    /** Together one vote: the most common wool width, a tie going to the larger. */
    static final String[] WOOL = {
        BLOCKS + "wool_colored_white.png", BLOCKS + "wool_colored_orange.png", BLOCKS + "wool_colored_magenta.png",
        BLOCKS + "wool_colored_light_blue.png", BLOCKS + "wool_colored_yellow.png", BLOCKS + "wool_colored_lime.png",
        BLOCKS + "wool_colored_pink.png", BLOCKS + "wool_colored_gray.png", BLOCKS + "wool_colored_silver.png",
        BLOCKS + "wool_colored_cyan.png", BLOCKS + "wool_colored_purple.png", BLOCKS + "wool_colored_blue.png",
        BLOCKS + "wool_colored_brown.png", BLOCKS + "wool_colored_green.png", BLOCKS + "wool_colored_red.png",
        BLOCKS + "wool_colored_black.png",
    };

    /** A texture's PNG width inside one pack, or 0 when it is missing or unreadable. */
    private interface Textures {
        int width(String path) throws IOException;
    }

    private ResolutionScanner() {}

    /** Never throws. UNKNOWN when the pack cannot be opened. */
    public static PackResolution scan(File pack) {
        // Vanilla shows the file or folder name as the pack's name.
        if (NameResolution.isOverlay(pack.getName())) {
            return PackResolution.OVERLAY;
        }
        int stated = NameResolution.find(pack.getName());
        if (stated > 0) {
            return PackResolution.stated(stated);
        }
        try {
            if (pack.isDirectory()) {
                File mcmeta = new File(pack, MCMETA);
                if (mcmeta.isFile()) {
                    try (InputStream in = new FileInputStream(mcmeta)) {
                        stated = NameResolution.find(NameResolution.description(in));
                    }
                    if (stated > 0) {
                        return PackResolution.stated(stated);
                    }
                }
                return vote(path -> {
                    File f = new File(pack, path);
                    if (!f.isFile()) {
                        return 0;
                    }
                    try (InputStream in = new FileInputStream(f)) {
                        return PngHeader.width(in);
                    }
                });
            } else {
                try (ZipFile zip = new ZipFile(pack)) {
                    ZipEntry mcmeta = zip.getEntry(MCMETA);
                    if (mcmeta != null) {
                        try (InputStream in = zip.getInputStream(mcmeta)) {
                            stated = NameResolution.find(NameResolution.description(in));
                        }
                        if (stated > 0) {
                            return PackResolution.stated(stated);
                        }
                    }
                    return vote(path -> {
                        ZipEntry entry = zip.getEntry(path);
                        if (entry == null) {
                            return 0;
                        }
                        try (InputStream in = zip.getInputStream(entry)) {
                            return PngHeader.width(in);
                        }
                    });
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.LOG.debug("packview: could not scan {}: {}", pack.getName(), e.toString());
            return PackResolution.UNKNOWN;
        }
    }

    private static PackResolution vote(Textures pack) throws IOException {
        List<Integer> widths = new ArrayList<>();
        for (String sample : SAMPLES) {
            addWidth(widths, pack.width(sample));
        }
        for (String layer : ARMOR_LAYERS) {
            addWidth(widths, pack.width(layer) / 4);
        }
        List<Integer> wool = new ArrayList<>();
        for (String block : WOOL) {
            addWidth(wool, pack.width(block));
        }
        if (!wool.isEmpty()) {
            widths.add(PackResolution.mostCommon(wool));
        }
        return PackResolution.fromWidths(widths);
    }

    private static void addWidth(List<Integer> widths, int w) {
        if (w > 0) {
            widths.add(w);
        }
    }
}
