package io.github.as9pa.packorganizer.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The pack textures the player preview draws: armor, held items, projectiles, particles and GUI sheets. Paths are
 * relative to {@link #ROOT}. No Minecraft classes.
 */
public final class ModelSpec {
    /** Prefix of every path below inside a pack. */
    public static final String ROOT = "assets/minecraft/textures/";
    /** Armor material file names, from the armor button order. */
    public static final List<String> MATERIALS = Collections.unmodifiableList(Arrays.asList(
            "leather", "chainmail", "iron", "gold", "diamond"));
    /** Sword file names matched to {@link #MATERIALS} by index. */
    private static final List<String> SWORDS = Arrays.asList("wood", "stone", "iron", "gold", "diamond");

    public static final String LEATHER_HELMET_OVERLAY = "items/leather_helmet_overlay.png";
    public static final String BOW = "items/bow_standby.png";
    /** Bow draw stages 0..2. */
    public static final String[] BOW_PULL = {
        "items/bow_pulling_0.png", "items/bow_pulling_1.png", "items/bow_pulling_2.png"
    };
    public static final String ENDER_PEARL = "items/ender_pearl.png";
    public static final String GOLDEN_APPLE = "items/apple_golden.png";
    public static final String POTION_OVERLAY = "items/potion_overlay.png";
    public static final String POTION_SPLASH = "items/potion_bottle_splash.png";
    public static final String ROD = "items/fishing_rod_uncast.png";
    public static final String ROD_CAST = "items/fishing_rod_cast.png";
    public static final String BOOK = "items/book_enchanted.png";
    public static final String ARROW = "entity/arrow.png";
    public static final String PARTICLES = "particle/particles.png";
    public static final String INVENTORY = "gui/container/inventory.png";
    public static final String ICONS = "gui/icons.png";
    public static final String GLINT = "misc/enchanted_item_glint.png";

    /** Every texture above, once each. */
    public static final List<String> PATHS = Collections.unmodifiableList(paths());

    private ModelSpec() {}

    /** Armor layer texture, {@code models/armor/<material>_layer_<1|2>.png}. */
    public static String armor(String material, int layer) {
        return "models/armor/" + check(material) + "_layer_" + checkLayer(layer) + ".png";
    }

    /** Leather armor's undyed overlay for a layer, drawn over the tinted base. */
    public static String armorOverlay(int layer) {
        return "models/armor/leather_layer_" + checkLayer(layer) + "_overlay.png";
    }

    /** Helmet item icon for an armor material. */
    public static String helmet(String material) {
        return "items/" + check(material) + "_helmet.png";
    }

    /** Sword held with an armor material: leather gets wood, chainmail gets stone, the rest share the name. */
    public static String sword(String material) {
        return "items/" + SWORDS.get(MATERIALS.indexOf(check(material))) + "_sword.png";
    }

    private static String check(String material) {
        if (!MATERIALS.contains(material)) {
            throw new IllegalArgumentException("unknown armor material: " + material);
        }
        return material;
    }

    private static int checkLayer(int layer) {
        if (layer != 1 && layer != 2) {
            throw new IllegalArgumentException("armor layer must be 1 or 2: " + layer);
        }
        return layer;
    }

    private static List<String> paths() {
        List<String> out = new ArrayList<>();
        for (String m : MATERIALS) {
            out.add(armor(m, 1));
            out.add(armor(m, 2));
        }
        out.add(armorOverlay(1));
        out.add(armorOverlay(2));
        for (String m : MATERIALS) {
            out.add(helmet(m));
        }
        out.add(LEATHER_HELMET_OVERLAY);
        for (String m : MATERIALS) {
            out.add(sword(m));
        }
        out.add(BOW);
        out.addAll(Arrays.asList(BOW_PULL));
        out.addAll(Arrays.asList(ENDER_PEARL, GOLDEN_APPLE, POTION_OVERLAY, POTION_SPLASH, ROD, ROD_CAST, BOOK,
                ARROW, PARTICLES, INVENTORY, ICONS, GLINT));
        return out;
    }
}
