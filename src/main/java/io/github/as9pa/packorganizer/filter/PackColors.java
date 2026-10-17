package io.github.as9pa.packorganizer.filter;

import java.util.Arrays;

/**
 * A pack's diamond sword and diamond armor color features. Either is null when the pack does not override that
 * texture (the color filter then falls back to the default pack's features).
 */
public final class PackColors {
    public static final PackColors NONE = new PackColors(null, null);

    public final ColorFeatures sword;
    public final ColorFeatures armor;

    public PackColors(ColorFeatures sword, ColorFeatures armor) {
        this.sword = sword;
        this.armor = armor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PackColors)) {
            return false;
        }
        PackColors other = (PackColors) o;
        return same(sword, other.sword) && same(armor, other.armor);
    }

    @Override
    public int hashCode() {
        return 31 * hash(sword) + hash(armor);
    }

    private static boolean same(ColorFeatures a, ColorFeatures b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.dark == b.dark && a.white == b.white && Arrays.equals(a.bins, b.bins);
    }

    private static int hash(ColorFeatures f) {
        if (f == null) {
            return 0;
        }
        return 31 * (31 * Double.hashCode(f.dark) + Double.hashCode(f.white)) + Arrays.hashCode(f.bins);
    }
}
