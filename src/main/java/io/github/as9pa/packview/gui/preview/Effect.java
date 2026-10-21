package io.github.as9pa.packview.gui.preview;

/** A running potion effect of the preview player, as the inventory effect list shows it. */
public final class Effect {
    /** Display name with the level, as the inventory list writes it. */
    public final String name;
    /** Icon cell in inventory.png's 18 px grid from (0, 198), 8 to a row (Potion.getStatusIconIndex). */
    public final int iconIndex;
    /** Potion liquid color, 0xRRGGBB. */
    public final int color;
    public final int amplifier;
    public int ticksLeft;

    Effect(String name, int iconIndex, int color, int amplifier, int ticksLeft) {
        this.name = name;
        this.iconIndex = iconIndex;
        this.color = color;
        this.amplifier = amplifier;
        this.ticksLeft = ticksLeft;
    }

    /** The golden apple's ItemFood potion: Regeneration II for 5 s. */
    static Effect regeneration() {
        return new Effect("Regeneration II", 7, 0xCD5CAB, 1, 100);
    }

    /** ItemAppleGold.onFoodEaten: Absorption for 2 min. */
    static Effect absorption() {
        return new Effect("Absorption", 18, 0x2552A5, 0, 2400);
    }
}
