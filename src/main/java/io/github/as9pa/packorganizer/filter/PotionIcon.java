package io.github.as9pa.packorganizer.filter;

import java.awt.image.BufferedImage;

/**
 * The preview strip's potion cell: a splash potion of healing drawn the way vanilla ItemPotion draws it, the overlay
 * tinted by the potion color under the untinted bottle. No Minecraft classes.
 */
public final class PotionIcon {
    /** Healing's potion color. */
    public static final int HEALING = 0xF82423;

    private PotionIcon() {}

    /**
     * The healing splash potion with each layer taken from the pack when it has one, else from vanilla, so a pack's
     * own bottle sits over the vanilla overlay and vice versa. Null when no layer is available.
     */
    public static BufferedImage healing(BufferedImage packBottle, BufferedImage packOverlay,
            BufferedImage vanillaBottle, BufferedImage vanillaOverlay) {
        return compose(packBottle != null ? packBottle : vanillaBottle,
                packOverlay != null ? packOverlay : vanillaOverlay, HEALING);
    }

    /**
     * {@code overlay} tinted by {@code rgb} with {@code bottle} drawn over it, source-over. The smaller layer is
     * scaled up to the larger with nearest neighbour. A null layer is left out; null when both are.
     */
    public static BufferedImage compose(BufferedImage bottle, BufferedImage overlay, int rgb) {
        if (bottle == null && overlay == null) {
            return null;
        }
        int w = Math.max(bottle == null ? 0 : bottle.getWidth(), overlay == null ? 0 : overlay.getWidth());
        int h = Math.max(bottle == null ? 0 : bottle.getHeight(), overlay == null ? 0 : overlay.getHeight());
        int[] argb = overlay == null ? new int[w * h] : scaled(overlay, w, h);
        tint(argb, rgb);
        if (bottle != null) {
            int[] top = scaled(bottle, w, h);
            for (int i = 0; i < argb.length; i++) {
                argb[i] = over(top[i], argb[i]);
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, argb, 0, w);
        return out;
    }

    /** Multiplies each pixel's color channels by {@code rgb} like a GL color tint, rounded; alpha is kept. */
    static void tint(int[] argb, int rgb) {
        int tr = (rgb >> 16) & 0xFF;
        int tg = (rgb >> 8) & 0xFF;
        int tb = rgb & 0xFF;
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int r = (((c >> 16) & 0xFF) * tr + 127) / 255;
            int g = (((c >> 8) & 0xFF) * tg + 127) / 255;
            int b = ((c & 0xFF) * tb + 127) / 255;
            argb[i] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
    }

    /** Non-premultiplied ARGB {@code src} drawn over {@code dst} with source-over alpha, rounded. */
    static int over(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 255) {
            return src;
        }
        if (sa == 0) {
            return dst;
        }
        int sw = sa * 255;
        int dw = (dst >>> 24) * (255 - sa);
        int total = sw + dw;
        int a = (total + 127) / 255;
        int r = (((src >> 16) & 0xFF) * sw + ((dst >> 16) & 0xFF) * dw + total / 2) / total;
        int g = (((src >> 8) & 0xFF) * sw + ((dst >> 8) & 0xFF) * dw + total / 2) / total;
        int b = ((src & 0xFF) * sw + (dst & 0xFF) * dw + total / 2) / total;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** The pixels of {@code img} resized to {@code w * h} with nearest neighbour, row-major. */
    static int[] scaled(BufferedImage img, int w, int h) {
        int sw = img.getWidth();
        int sh = img.getHeight();
        int[] src = img.getRGB(0, 0, sw, sh, null, 0, sw);
        if (sw == w && sh == h) {
            return src;
        }
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int sy = y * sh / h;
            for (int x = 0; x < w; x++) {
                out[y * w + x] = src[sy * sw + x * sw / w];
            }
        }
        return out;
    }
}
