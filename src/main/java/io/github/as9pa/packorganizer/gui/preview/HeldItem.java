package io.github.as9pa.packorganizer.gui.preview;

/** The item in the preview player's hand; ids are the {@code previewItem} values in the settings. */
public enum HeldItem {
    SWORD("sword"),
    BOW("bow"),
    ENDER_PEARL("ender_pearl"),
    GOLDEN_APPLE("golden_apple"),
    SPLASH_POTION("splash_potion"),
    FISHING_ROD("fishing_rod");

    public final String id;

    HeldItem(String id) {
        this.id = id;
    }

    /** The item with this settings id; unknown or missing ids give the sword, the settings default. */
    public static HeldItem fromId(String id) {
        for (HeldItem item : values()) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return SWORD;
    }
}
