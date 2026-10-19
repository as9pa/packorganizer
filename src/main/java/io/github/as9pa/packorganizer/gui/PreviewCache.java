package io.github.as9pa.packorganizer.gui;

import io.github.as9pa.packorganizer.Log;
import io.github.as9pa.packorganizer.filter.PackTextureReader;
import io.github.as9pa.packorganizer.filter.PotionIcon;
import io.github.as9pa.packorganizer.filter.PreviewSpec;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

/**
 * Hover previews: the 16 {@link PreviewSpec} textures of a pack as one GL texture. Loading runs on a single
 * background thread; the client thread uploads the finished atlas. Cells the pack does not override show the
 * default pack texture and are flagged missing. All public methods are client thread only.
 *
 * <p>The loader serves the newest request first and skips requests gone stale ({@link LoadQueue}), so after a fast
 * scroll the rows left on screen load before the ones scrolled past.
 */
public final class PreviewCache {
    private static final int MAX_PACKS = 96;
    private static final String ASSETS = "assets/minecraft/";
    /** A pending request not renewed for this long is dropped; on-screen rows renew theirs every frame. */
    private static final long STALE_MS = 500;

    /** Guards QUEUE and Slot.taken; the loader waits on it while the queue is empty. */
    private static final Object LOCK = new Object();
    private static final LoadQueue<Slot> QUEUE = new LoadQueue<>(STALE_MS);
    /** Started by the first request; client thread only. */
    private static Thread loader;

    /** Access-ordered so the first entry is the least recently hovered. */
    private static final LinkedHashMap<File, Slot> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * Default pack textures in {@link PreviewSpec#LAYER_PATHS} order, the raw potion layers included, loaded once on
     * the loader thread; null entries when the default lacks them.
     */
    private static BufferedImage[] defaults;

    private PreviewCache() {}

    /** A ready preview: the atlas texture and which cells fell back to the default pack. */
    public static final class Preview {
        public final ResourceLocation texture;
        private final boolean[] missing;

        Preview(ResourceLocation texture, boolean[] missing) {
            this.texture = texture;
            this.missing = missing;
        }

        public boolean missing(int cell) {
            return missing[cell];
        }
    }

    private static final class Slot {
        final File pack;
        final IResourcePack defaultPack;
        /** Set under LOCK once the loader takes the slot; a taken slot is never queued again. */
        volatile boolean taken;
        /** Written by the loader thread when done. */
        volatile BufferedImage atlas;
        volatile boolean[] missing;
        Preview preview;

        Slot(File pack, IResourcePack defaultPack) {
            this.pack = pack;
            this.defaultPack = defaultPack;
        }
    }

    /**
     * The preview for a pack, or null while it loads. The first call for a pack queues the load, and every later
     * call before the loader takes it renews the request, so a pack that stays on screen is not dropped as stale.
     */
    public static Preview get(File pack) {
        Slot slot = CACHE.get(pack);
        if (slot == null) {
            slot = new Slot(pack, Minecraft.getMinecraft().getResourcePackRepository().rprDefaultResourcePack);
            CACHE.put(pack, slot);
            evict();
        }
        if (slot.preview == null && slot.atlas != null) {
            DynamicTexture texture = new DynamicTexture(slot.atlas);
            ResourceLocation location = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("packorganizer_preview", texture);
            slot.preview = new Preview(location, slot.missing);
            slot.atlas = null;
        }
        if (slot.preview == null && !slot.taken) {
            request(slot);
        }
        return slot.preview;
    }

    /** Drops every preview and its GL texture, and every pending load. Call when the resource packs screen closes. */
    public static void clear() {
        synchronized (LOCK) {
            QUEUE.clear();
        }
        for (Slot slot : CACHE.values()) {
            release(slot);
        }
        CACHE.clear();
    }

    /** Queues the slot, or renews its request, unless the loader has taken it meanwhile. */
    private static void request(Slot slot) {
        if (loader == null) {
            loader = new Thread(PreviewCache::loadLoop, "packorganizer-preview");
            loader.setDaemon(true);
            loader.start();
        }
        synchronized (LOCK) {
            if (!slot.taken) {
                QUEUE.request(slot, now());
                LOCK.notify();
            }
        }
    }

    /** Loader thread: takes the newest live request, waiting while there is none. */
    private static void loadLoop() {
        try {
            while (true) {
                Slot slot;
                synchronized (LOCK) {
                    while ((slot = QUEUE.next(now())) == null) {
                        LOCK.wait();
                    }
                    slot.taken = true;
                }
                try {
                    load(slot.pack, slot.defaultPack, slot);
                } catch (Error e) {
                    // Keep the one loader alive for the next pack.
                    Log.LOG.debug("packorganizer: preview build failed for {}: {}", slot.pack.getName(), e.toString());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    private static void evict() {
        Iterator<Map.Entry<File, Slot>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_PACKS && it.hasNext()) {
            Slot slot = it.next().getValue();
            synchronized (LOCK) {
                QUEUE.remove(slot);
            }
            release(slot);
            it.remove();
        }
    }

    private static void release(Slot slot) {
        if (slot.preview != null) {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(slot.preview.texture);
            slot.preview = null;
        }
        slot.atlas = null;
    }

    private static void load(File pack, IResourcePack defaultPack, Slot slot) {
        try {
            BufferedImage[] own = PackTextureReader.readLayers(pack);
            BufferedImage[] fallback = defaults(defaultPack);
            BufferedImage[] cells = Arrays.copyOf(own, PreviewSpec.CELLS);
            boolean[] missing = new boolean[PreviewSpec.CELLS];
            for (int i = 0; i < PreviewSpec.CELLS; i++) {
                if (cells[i] == null) {
                    missing[i] = true;
                    cells[i] = fallback[i];
                }
            }
            int potion = PreviewSpec.POTION;
            int overlay = PreviewSpec.OVERLAY;
            missing[potion] = own[potion] == null && own[overlay] == null;
            cells[potion] = PotionIcon.healing(own[potion], own[overlay], fallback[potion], fallback[overlay]);
            int cell = PreviewSpec.cellSize(Arrays.asList(cells));
            BufferedImage atlas = new BufferedImage(PreviewSpec.atlasWidth(cell), PreviewSpec.atlasHeight(cell),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = atlas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (int i = 0; i < PreviewSpec.CELLS; i++) {
                if (cells[i] != null) {
                    g.drawImage(cells[i], PreviewSpec.cellX(i, cell), PreviewSpec.cellY(i, cell), cell, cell, null);
                }
            }
            g.dispose();
            slot.missing = missing;
            slot.atlas = atlas;
        } catch (RuntimeException e) {
            Log.LOG.debug("packorganizer: preview build failed for {}: {}", pack.getName(), e.toString());
        }
    }

    /** Loader thread only. */
    private static BufferedImage[] defaults(IResourcePack defaultPack) {
        if (defaults != null) {
            return defaults;
        }
        BufferedImage[] out = new BufferedImage[PreviewSpec.LAYER_PATHS.size()];
        for (int i = 0; i < out.length; i++) {
            String path = PreviewSpec.LAYER_PATHS.get(i).substring(ASSETS.length());
            try (InputStream in = defaultPack.getInputStream(new ResourceLocation("minecraft", path))) {
                BufferedImage img = in == null ? null : ImageIO.read(in);
                if (img != null) {
                    out[i] = i == PreviewSpec.HEART ? PreviewSpec.cropHeart(img) : PreviewSpec.firstFrame(img);
                }
            } catch (IOException | RuntimeException e) {
                // leave the cell empty
            }
        }
        defaults = out;
        return out;
    }
}
