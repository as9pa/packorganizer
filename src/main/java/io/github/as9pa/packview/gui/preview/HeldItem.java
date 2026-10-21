package io.github.as9pa.packview.gui.preview;

/** The item in the preview player's hand; ids are the item button ids in {@code Settings.ITEMS}. */
public enum HeldItem {
    /** The empty hand: nothing drawn, the vanilla empty arm, a left click punches. */
    NONE("none"),
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

    /** The item with this id; unknown or missing ids give the empty hand, the preview's start. */
    public static HeldItem fromId(String id) {
        for (HeldItem item : values()) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return NONE;
    }

    /**
     * The selection after a click on {@code clicked}: it is picked, or dropped to {@code none} when it was already
     * picked. Used for the armor and item buttons.
     */
    public static <T> T toggle(T current, T clicked, T none) {
        return clicked.equals(current) ? none : clicked;
    }
}
