package io.github.as9pa.packorganizer.gui;

import io.github.as9pa.packorganizer.Log;
import io.github.as9pa.packorganizer.filter.PackTextureReader;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

/**
 * Hover previews: the 16 {@link PreviewSpec} textures of a pack as one GL texture. Loading runs on a single
 * background thread; the client thread uploads the finished atlas. Cells the pack does not override show the
 * default pack texture and are flagged missing. All public methods are client thread only.
 */
public final class PreviewCache {
    private static final int MAX_PACKS = 96;
    private static final String ASSETS = "assets/minecraft/";

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "packorganizer-preview");
        t.setDaemon(true);
        return t;
    });

    /** Access-ordered so the first entry is the least recently hovered. */
    private static final LinkedHashMap<File, Slot> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    /** Default pack textures, loaded once on the loader thread; null cells when the default lacks them. */
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
        /** Written by the loader thread when done. */
        volatile BufferedImage atlas;
        volatile boolean[] missing;
        Preview preview;
    }

    /** The preview for a pack, or null while it loads. The first call for a pack starts the load. */
    public static Preview get(File pack) {
        Slot slot = CACHE.get(pack);
        if (slot == null) {
            slot = new Slot();
            CACHE.put(pack, slot);
            evict();
            Slot target = slot;
            IResourcePack defaultPack = Minecraft.getMinecraft().getResourcePackRepository().rprDefaultResourcePack;
            LOADER.execute(() -> load(pack, defaultPack, target));
            return null;
        }
        if (slot.preview == null && slot.atlas != null) {
            DynamicTexture texture = new DynamicTexture(slot.atlas);
            ResourceLocation location = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("packorganizer_preview", texture);
            slot.preview = new Preview(location, slot.missing);
            slot.atlas = null;
        }
        return slot.preview;
    }

    /** Drops every preview and its GL texture. Call when the resource packs screen closes. */
    public static void clear() {
        for (Slot slot : CACHE.values()) {
            release(slot);
        }
        CACHE.clear();
    }

    private static void evict() {
        Iterator<Map.Entry<File, Slot>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_PACKS && it.hasNext()) {
            release(it.next().getValue());
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
            BufferedImage[] cells = PackTextureReader.read(pack);
            BufferedImage[] fallback = defaults(defaultPack);
            boolean[] missing = new boolean[PreviewSpec.CELLS];
            for (int i = 0; i < PreviewSpec.CELLS; i++) {
                if (cells[i] == null) {
                    missing[i] = true;
                    cells[i] = fallback[i];
                }
            }
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
        BufferedImage[] out = new BufferedImage[PreviewSpec.CELLS];
        for (int i = 0; i < PreviewSpec.CELLS; i++) {
            String path = PreviewSpec.PATHS.get(i).substring(ASSETS.length());
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
