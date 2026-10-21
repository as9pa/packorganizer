package io.github.as9pa.packview.gui.preview;

import io.github.as9pa.packview.Log;
import io.github.as9pa.packview.filter.ModelSpec;
import io.github.as9pa.packview.filter.PackTextureReader;
import io.github.as9pa.packview.filter.PreviewSpec;
import io.github.as9pa.packview.filter.SkyLayer;
import io.github.as9pa.packview.filter.SkyReader;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

/**
 * Player preview models per pack. Loading runs on a single background thread: the {@link ModelSpec} textures and a
 * shrunk sky first, then the sky again at full size as a later task so other packs are not held up behind it. GL
 * uploads happen lazily in {@link PackModel} on the client thread. All public methods are client thread only.
 */
public final class PreviewTextures {
    private static final int MAX_PACKS = 8;
    /** Sky width shown while the full size sky loads. */
    static final int SKY_PREVIEW_WIDTH = 384;
    /** Largest sky width ever uploaded. */
    static final int SKY_FULL_WIDTH = 1536;
    private static final String TEXTURES = "textures/";

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "packview-model");
        t.setDaemon(true);
        return t;
    });

    /** Access-ordered so the first entry is the least recently hovered. */
    private static final LinkedHashMap<File, Slot> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    /** The vanilla model; never evicted, dropped by {@link #clear()}. */
    private static Slot defaults;

    private PreviewTextures() {}

    private static final class Slot {
        /** Written by the loader thread when the textures and shrunk sky are ready. */
        volatile PackModel model;
        /** Set by the client thread when evicted or cleared; the loader skips work for it. */
        volatile boolean dropped;
    }

    /**
     * The model for a pack, or null while it loads. The first call for a pack starts the load. Textures the pack
     * lacks come from {@link #defaults()}.
     */
    public static PackModel get(File pack) {
        Slot fallback = defaultsSlot();
        Slot slot = CACHE.get(pack);
        if (slot == null) {
            slot = new Slot();
            CACHE.put(pack, slot);
            evict();
            Slot target = slot;
            LOADER.execute(() -> load(pack, fallback, target));
            return null;
        }
        return slot.model;
    }

    /** The vanilla model from the default pack, or null while it loads. The first call starts the load. */
    public static PackModel defaults() {
        return defaultsSlot().model;
    }

    /**
     * Drops every model with its GL textures and display lists, the vanilla one included. Call when the resource
     * packs screen closes; models handed out before return 0 from their GL methods afterwards.
     */
    public static void clear() {
        for (Slot slot : CACHE.values()) {
            release(slot);
        }
        CACHE.clear();
        if (defaults != null) {
            release(defaults);
            defaults = null;
        }
    }

    private static Slot defaultsSlot() {
        if (defaults == null) {
            defaults = new Slot();
            Slot target = defaults;
            IResourcePack defaultPack = Minecraft.getMinecraft().getResourcePackRepository().rprDefaultResourcePack;
            LOADER.execute(() -> loadDefaults(defaultPack, target));
        }
        return defaults;
    }

    private static void evict() {
        Iterator<Map.Entry<File, Slot>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_PACKS && it.hasNext()) {
            release(it.next().getValue());
            it.remove();
        }
    }

    private static void release(Slot slot) {
        slot.dropped = true;
        PackModel model = slot.model;
        if (model != null) {
            model.release();
        }
    }

    /** Loader thread. The vanilla model is queued first, so {@code fallback.model} is set by the time this runs. */
    private static void load(File pack, Slot fallback, Slot slot) {
        if (slot.dropped) {
            return;
        }
        PackModel model;
        List<SkyLayer> sky = new ArrayList<>();
        List<BufferedImage> low = new ArrayList<>();
        try {
            Map<String, BufferedImage> images = PackTextureReader.readPaths(pack, ModelSpec.PATHS);
            for (SkyLayer layer : SkyReader.layers(pack)) {
                BufferedImage img = SkyReader.image(pack, layer.source, SKY_PREVIEW_WIDTH);
                if (img != null) {
                    sky.add(layer);
                    low.add(img);
                }
            }
            model = new PackModel(images, sky, low.toArray(new BufferedImage[0]), fallback.model);
        } catch (RuntimeException e) {
            Log.LOG.debug("packview: model load failed for {}: {}", pack.getName(), e.toString());
            sky.clear();
            low.clear();
            model = new PackModel(Collections.emptyMap(), sky, new BufferedImage[0], fallback.model);
        }
        slot.model = model;
        boolean[] shrunk = new boolean[low.size()];
        boolean any = false;
        for (int i = 0; i < shrunk.length; i++) {
            // A shrunk image is exactly the preview width; a smaller one is already full size.
            shrunk[i] = low.get(i).getWidth() >= SKY_PREVIEW_WIDTH;
            any |= shrunk[i];
        }
        if (any) {
            PackModel target = model;
            LOADER.execute(() -> loadFullSky(pack, slot, target, sky, shrunk));
        }
    }

    /** Loader thread: re-reads the shrunk sky layers at full size and hands them to the model. */
    private static void loadFullSky(File pack, Slot slot, PackModel model, List<SkyLayer> sky, boolean[] shrunk) {
        try {
            BufferedImage[] high = new BufferedImage[sky.size()];
            boolean any = false;
            for (int i = 0; i < high.length; i++) {
                if (slot.dropped || model.released) {
                    return;
                }
                if (shrunk[i]) {
                    high[i] = SkyReader.image(pack, sky.get(i).source, SKY_FULL_WIDTH);
                    any |= high[i] != null;
                }
            }
            if (any && !model.released) {
                model.skyHigh = high;
            }
        } catch (RuntimeException e) {
            Log.LOG.debug("packview: full sky load failed for {}: {}", pack.getName(), e.toString());
        }
    }

    /** Loader thread. Always sets a model, an empty one if the default pack cannot be read. */
    private static void loadDefaults(IResourcePack defaultPack, Slot slot) {
        PackModel model;
        try {
            Map<String, BufferedImage> images = new HashMap<>();
            for (String rel : ModelSpec.PATHS) {
                try (InputStream in = defaultPack.getInputStream(new ResourceLocation("minecraft", TEXTURES + rel))) {
                    BufferedImage img = in == null ? null : ImageIO.read(in);
                    if (img != null) {
                        images.put(rel, rel.startsWith("items/") ? PreviewSpec.firstFrame(img) : img);
                    }
                } catch (IOException | RuntimeException e) {
                    // leave it out
                }
            }
            model = new PackModel(images, Collections.<SkyLayer>emptyList(), new BufferedImage[0], null);
        } catch (RuntimeException e) {
            Log.LOG.debug("packview: vanilla model load failed: {}", e.toString());
            model = new PackModel(Collections.emptyMap(), Collections.<SkyLayer>emptyList(), new BufferedImage[0],
                    null);
        }
        slot.model = model;
    }
}
