package io.github.as9pa.packorganizer.gui.preview;

import io.github.as9pa.packorganizer.Log;
import io.github.as9pa.packorganizer.filter.ModelSpec;
import io.github.as9pa.packorganizer.filter.SkyLayer;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;

/**
 * One pack's player preview textures: the {@link ModelSpec#PATHS} images, helmet icon bounds and sky layers. Built
 * on the {@link PreviewTextures} loader thread; the GL methods upload lazily and are client thread only. Paths the
 * pack lacks resolve to the vanilla model passed as fallback. Leather armor and the leather helmet are tinted
 * {@link #LEATHER_TINT} here, their overlays are kept untinted as their own textures.
 */
public final class PackModel {
    /** Undyed leather armor color, as vanilla. */
    static final int LEATHER_TINT = 0xA06540;
    /** How long the full size sky fades in over the shrunk one. */
    static final long SKY_FADE_MS = 300;
    /** Full size sky layers upload at least this far apart so a big sky does not stall a single frame. */
    private static final long SKY_UPLOAD_GAP_MS = 8;

    /** As uploaded (leather tinted); never modified after construction. */
    private final Map<String, BufferedImage> images;
    private final Map<String, int[]> helmetBounds = new HashMap<>();
    private final List<SkyLayer> sky;
    private final PackModel fallback;

    // Client thread only from here down, except where noted.
    private final Map<String, Integer> textures = new HashMap<>();
    private final Map<String, Integer> lists = new HashMap<>();
    /** Shrunk sky images until the first sky call uploads them. */
    private BufferedImage[] skyLow;
    private final int[] skyLowIds;
    private final int[] skyHighIds;
    /**
     * Full size sky images, written once by the loader thread; null entries are layers whose shrunk image is already
     * full size. The client thread nulls entries as it uploads them.
     */
    volatile BufferedImage[] skyHigh;
    private int nextHigh;
    private long lastHighUpload;
    /** When the last full size layer was uploaded, or -1 before that. */
    private long fadeStart = -1;
    /** Set by the client thread; the loader thread reads it to skip a full size sky nobody will draw. */
    volatile boolean released;

    /**
     * Loader thread. {@code images} are keyed by path relative to {@link ModelSpec#ROOT}; {@code skyLow} holds one
     * shrunk image per entry of {@code sky}. {@code fallback} is the vanilla model, null for the vanilla model itself.
     */
    PackModel(Map<String, BufferedImage> images, List<SkyLayer> sky, BufferedImage[] skyLow, PackModel fallback) {
        Map<String, BufferedImage> own = new HashMap<>(images);
        for (String path : new String[] {
                ModelSpec.armor("leather", 1), ModelSpec.armor("leather", 2), ModelSpec.helmet("leather")}) {
            BufferedImage img = own.get(path);
            if (img != null) {
                own.put(path, tinted(img, LEATHER_TINT));
            }
        }
        this.images = own;
        this.sky = Collections.unmodifiableList(new ArrayList<>(sky));
        this.skyLow = skyLow;
        this.skyLowIds = new int[sky.size()];
        this.skyHighIds = new int[sky.size()];
        this.fallback = fallback;
        for (String material : ModelSpec.MATERIALS) {
            helmetBounds.put(material, iconBounds(material));
        }
    }

    /** The image for a path relative to {@link ModelSpec#ROOT} as uploaded, or vanilla's; null if neither has it. */
    public BufferedImage image(String path) {
        BufferedImage img = images.get(path);
        return img != null || fallback == null ? img : fallback.image(path);
    }

    /** GL texture id for a path relative to {@link ModelSpec#ROOT}, vanilla's when the pack lacks it, 0 if none. */
    public int texture(String path) {
        if (released) {
            return 0;
        }
        Integer id = textures.get(path);
        if (id != null) {
            return id;
        }
        BufferedImage img = images.get(path);
        if (img == null) {
            return fallback == null ? 0 : fallback.texture(path);
        }
        int gl = upload(img, false);
        textures.put(path, gl);
        return gl;
    }

    /**
     * Display list of the item texture at a path extruded by {@link ItemExtruder}, built on first use; draw it with
     * {@link #texture(String)} of the same path bound. Vanilla's when the pack lacks it, 0 if none.
     */
    public int itemList(String path) {
        if (released) {
            return 0;
        }
        Integer list = lists.get(path);
        if (list != null) {
            return list;
        }
        BufferedImage img = images.get(path);
        if (img == null) {
            return fallback == null ? 0 : fallback.itemList(path);
        }
        int id = 0;
        try {
            id = ItemExtruder.compile(ItemExtruder.build(pixels(img), img.getWidth(), img.getHeight()));
        } catch (RuntimeException e) {
            Log.LOG.debug("packorganizer: item extrusion failed for {}: {}", path, e.toString());
        }
        lists.put(path, id);
        return id;
    }

    /** The pack's sky layers in drawing order; empty without a custom sky. Thread safe. */
    public List<SkyLayer> sky() {
        return sky;
    }

    /**
     * GL texture of a sky layer: the shrunk image until every full size layer is uploaded, then the full size one.
     * 0 for a bad index.
     */
    public int skyTexture(int layer) {
        advanceSky();
        if (layer < 0 || layer >= skyLowIds.length) {
            return 0;
        }
        return fadeStart >= 0 && skyHighIds[layer] != 0 ? skyHighIds[layer] : skyLowIds[layer];
    }

    /**
     * The shrunk texture of a layer while the full size one fades in, else 0. When it is not 0, draw it weighted
     * {@code 1 - skyFade()} and {@link #skyTexture(int)} weighted {@code skyFade()}; otherwise draw
     * {@link #skyTexture(int)} alone.
     */
    public int skyLowTexture(int layer) {
        advanceSky();
        if (layer < 0 || layer >= skyLowIds.length || !fading()) {
            return 0;
        }
        return skyHighIds[layer] != 0 ? skyLowIds[layer] : 0;
    }

    /** 0 to 1 over {@link #SKY_FADE_MS} after the full size sky is uploaded; 1 when no fade is running. */
    public float skyFade() {
        advanceSky();
        if (!fading()) {
            return 1F;
        }
        return Math.min(1F, (Minecraft.getSystemTime() - fadeStart) / (float) SKY_FADE_MS);
    }

    /**
     * Opaque bounds {x, y, width, height} of a material's helmet icon in its texels, the leather overlay included;
     * the whole image when nothing is drawn, all zero when there is no icon. Thread safe.
     */
    public int[] helmetIconBounds(String material) {
        int[] b = helmetBounds.get(material);
        return b == null ? new int[4] : b.clone();
    }

    /** Frees every GL texture and display list this model made; later GL calls return 0. Client thread only. */
    void release() {
        released = true;
        for (int id : textures.values()) {
            TextureUtil.deleteTexture(id);
        }
        textures.clear();
        for (int list : lists.values()) {
            ItemExtruder.delete(list);
        }
        lists.clear();
        for (int i = 0; i < skyLowIds.length; i++) {
            deleteTexture(skyLowIds[i]);
            deleteTexture(skyHighIds[i]);
            skyLowIds[i] = 0;
            skyHighIds[i] = 0;
        }
        skyLow = null;
        skyHigh = null;
    }

    private boolean fading() {
        return fadeStart >= 0 && Minecraft.getSystemTime() - fadeStart < SKY_FADE_MS;
    }

    /** Uploads the shrunk sky on first use, then one full size layer per call at most every gap. */
    private void advanceSky() {
        if (released || skyLowIds.length == 0) {
            return;
        }
        if (skyLow != null) {
            for (int i = 0; i < skyLow.length; i++) {
                skyLowIds[i] = skyLow[i] == null ? 0 : upload(skyLow[i], true);
            }
            skyLow = null;
        }
        BufferedImage[] high = skyHigh;
        if (high == null || fadeStart >= 0) {
            return;
        }
        long now = Minecraft.getSystemTime();
        nextHigh = nextPending(high, nextHigh);
        if (nextHigh < high.length && now - lastHighUpload >= SKY_UPLOAD_GAP_MS) {
            skyHighIds[nextHigh] = upload(high[nextHigh], true);
            high[nextHigh] = null;
            lastHighUpload = now;
            nextHigh = nextPending(high, nextHigh + 1);
        }
        if (nextHigh >= high.length) {
            fadeStart = now;
            skyHigh = null;
        }
    }

    private static int nextPending(BufferedImage[] high, int from) {
        int i = from;
        while (i < high.length && high[i] == null) {
            i++;
        }
        return i;
    }

    /** Sky textures are smoothed and clamped; item and armor textures stay sharp like vanilla. */
    private static int upload(BufferedImage img, boolean sky) {
        int id = TextureUtil.glGenTextures();
        try {
            TextureUtil.uploadTextureImageAllocate(id, img, sky, sky);
            return id;
        } catch (RuntimeException e) {
            Log.LOG.debug("packorganizer: model texture upload failed: {}", e.toString());
            TextureUtil.deleteTexture(id);
            return 0;
        }
    }

    private static void deleteTexture(int id) {
        if (id > 0) {
            TextureUtil.deleteTexture(id);
        }
    }

    private int[] iconBounds(String material) {
        BufferedImage icon = image(ModelSpec.helmet(material));
        if (icon == null) {
            return new int[4];
        }
        int w = icon.getWidth();
        int h = icon.getHeight();
        int[] bounds = opaqueBounds(pixels(icon), w, h);
        if ("leather".equals(material)) {
            BufferedImage overlay = image(ModelSpec.LEATHER_HELMET_OVERLAY);
            if (overlay != null && overlay.getWidth() == w && overlay.getHeight() == h) {
                bounds = union(bounds, opaqueBounds(pixels(overlay), w, h));
            }
        }
        return bounds == null ? new int[] {0, 0, w, h} : bounds;
    }

    private static int[] pixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    private static BufferedImage tinted(BufferedImage img, int rgb) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] argb = pixels(img);
        tint(argb, rgb);
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

    /**
     * Bounds {x, y, width, height} of the pixels with alpha above 0 in a row-major {@code w * h} image, or null when
     * there are none.
     */
    static int[] opaqueBounds(int[] argb, int w, int h) {
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((argb[y * w + x] >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new int[] {minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    /** The box covering two {x, y, width, height} boxes; either may be null. */
    static int[] union(int[] a, int[] b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        int x = Math.min(a[0], b[0]);
        int y = Math.min(a[1], b[1]);
        int right = Math.max(a[0] + a[2], b[0] + b[2]);
        int bottom = Math.max(a[1] + a[3], b[1] + b[3]);
        return new int[] {x, y, right - x, bottom - y};
    }
}
