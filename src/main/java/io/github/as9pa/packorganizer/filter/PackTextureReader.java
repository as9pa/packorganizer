package io.github.as9pa.packorganizer.filter;

import io.github.as9pa.packorganizer.Log;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Decodes a pack's preview textures with its own ZipFile or file reads, so it never races vanilla or fastpacks
 * closing the pack's IResourcePack. No Minecraft classes.
 */
public final class PackTextureReader {
    private PackTextureReader() {}

    /**
     * The {@link PreviewSpec#PATHS} textures of a zip or folder pack, in atlas order, with animation strips cut to
     * their first frame and the heart cropped from icons.png. A cell is null when the file is missing or unreadable.
     */
    public static BufferedImage[] read(File pack) {
        return Arrays.copyOf(readLayers(pack), PreviewSpec.CELLS);
    }

    /**
     * Like {@link #read} but over {@link PreviewSpec#LAYER_PATHS}: the cells followed by the potion overlay at
     * {@link PreviewSpec#OVERLAY}, cut to its first frame, or null when the pack has none.
     */
    public static BufferedImage[] readLayers(File pack) {
        BufferedImage[] out = new BufferedImage[PreviewSpec.LAYER_PATHS.size()];
        each(pack, PreviewSpec.LAYER_PATHS, (i, in) -> out[i] = prepare(i, decode(in)));
        return out;
    }

    /**
     * The given textures of a zip or folder pack, keyed by their path relative to {@link ModelSpec#ROOT}. Paths
     * under {@code items/} have animation strips cut to their first frame; others are returned as decoded. Missing
     * or unreadable files are left out of the map.
     */
    public static Map<String, BufferedImage> readPaths(File pack, List<String> relPaths) {
        List<String> entries = new ArrayList<>(relPaths.size());
        for (String rel : relPaths) {
            entries.add(ModelSpec.ROOT + rel);
        }
        Map<String, BufferedImage> out = new LinkedHashMap<>();
        each(pack, entries, (i, in) -> {
            BufferedImage img = decode(in);
            if (img != null) {
                String rel = relPaths.get(i);
                out.put(rel, rel.startsWith("items/") ? PreviewSpec.firstFrame(img) : img);
            }
        });
        return out;
    }

    /** Receives one opened texture; {@code index} is its position in the path list. */
    private interface EntrySink {
        void accept(int index, InputStream in) throws IOException;
    }

    /** Opens each path that exists in a folder or zip pack and hands it to the sink; unreadable files are skipped. */
    private static void each(File pack, List<String> paths, EntrySink sink) {
        if (pack.isDirectory()) {
            for (int i = 0; i < paths.size(); i++) {
                File f = new File(pack, paths.get(i));
                if (f.isFile()) {
                    try (InputStream in = new FileInputStream(f)) {
                        sink.accept(i, in);
                    } catch (IOException e) {
                        // leave the cell empty
                    }
                }
            }
            return;
        }
        if (!pack.isFile()) {
            return;
        }
        try (ZipFile zip = new ZipFile(pack)) {
            for (int i = 0; i < paths.size(); i++) {
                ZipEntry entry = zip.getEntry(paths.get(i));
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        sink.accept(i, in);
                    } catch (IOException e) {
                        // leave the cell empty
                    }
                }
            }
        } catch (IOException e) {
            Log.LOG.debug("packorganizer: preview read failed for {}: {}", pack.getName(), e.toString());
        }
    }

    private static BufferedImage decode(InputStream in) {
        try {
            return ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static BufferedImage prepare(int index, BufferedImage img) {
        if (img == null) {
            return null;
        }
        return index == PreviewSpec.HEART ? PreviewSpec.cropHeart(img) : PreviewSpec.firstFrame(img);
    }
}
