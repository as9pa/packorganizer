package io.github.as9pa.packview.filter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the scanner found in a pack: the texture resolution bucket, an overlay, or nothing usable. Immutable.
 * A resolution stated in the pack's name or description keeps its number for the badge; filters use the bucket.
 */
public final class PackResolution {
    public enum Kind { TEXTURES, OVERLAY, UNKNOWN }

    public static final PackResolution OVERLAY = new PackResolution(Kind.OVERLAY, 0, 0);
    public static final PackResolution UNKNOWN = new PackResolution(Kind.UNKNOWN, 0, 0);

    public final Kind kind;
    /** 16, 32, 64 or 128 for TEXTURES; 0 otherwise. */
    public final int res;
    /** The number the name or description states, e.g. 8 or 256; 0 when the textures decided. */
    public final int stated;

    private PackResolution(Kind kind, int res, int stated) {
        this.kind = kind;
        this.res = res;
        this.stated = stated;
    }

    public static PackResolution textures(int bucket) {
        return new PackResolution(Kind.TEXTURES, bucket, 0);
    }

    /** TEXTURES in the stated value's bucket, with the badge showing the stated value itself. */
    public static PackResolution stated(int value) {
        return new PackResolution(Kind.TEXTURES, bucket(value), value);
    }

    /** The width rounded down to a power of two, clamped to 16..128: 20 gives 16, 48 gives 32, 256 gives 128. */
    public static int bucket(int width) {
        if (width < 32) {
            return 16;
        }
        if (width < 64) {
            return 32;
        }
        if (width < 128) {
            return 64;
        }
        return 128;
    }

    /** Most common width wins; a tie goes to the larger width; no widths means an overlay pack. */
    public static PackResolution fromWidths(List<Integer> widths) {
        if (widths.isEmpty()) {
            return OVERLAY;
        }
        return textures(bucket(mostCommon(widths)));
    }

    /** The most common width, a tie going to the larger; 0 for no widths. */
    static int mostCommon(List<Integer> widths) {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = 0;
        int bestCount = 0;
        for (int w : widths) {
            Integer prev = counts.get(w);
            int c = prev == null ? 1 : prev + 1;
            counts.put(w, c);
            if (c > bestCount || (c == bestCount && w > best)) {
                best = w;
                bestCount = c;
            }
        }
        return best;
    }

    /** Text for the row badge: "16x" (or the stated "256x"), "overlay", or "" when nothing is known. */
    public String badge() {
        switch (kind) {
            case TEXTURES:
                return (stated > 0 ? stated : res) + "x";
            case OVERLAY:
                return "overlay";
            default:
                return "";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PackResolution)) {
            return false;
        }
        PackResolution other = (PackResolution) o;
        return kind == other.kind && res == other.res && stated == other.stated;
    }

    @Override
    public int hashCode() {
        return (kind.hashCode() * 31 + res) * 31 + stated;
    }

    @Override
    public String toString() {
        if (kind != Kind.TEXTURES) {
            return kind.name();
        }
        return stated > 0 ? "TEXTURES(" + res + ", stated " + stated + ")" : "TEXTURES(" + res + ")";
    }
}
