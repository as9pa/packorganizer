package io.github.as9pa.packview.filter;

import io.github.as9pa.packview.Log;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Decodes a pack's preview textures with its own ZipFile or file reads, so it never races vanilla or fastpacks
 * closing the pack's IResourcePack. No Minecraft classes.
 */
public final class PackTextureReader {
    private PackTextureReader() {}

    /**
     * The {@link PreviewSpec#PATHS} textures of a zip or folder pack, in atlas order, with animation strips cut to
     * their first frame and the heart cropped from icons.png, each subsampled to no less than
     * {@link PreviewSpec#MAX_CELL} wide. A cell is null when the file is missing or unreadable.
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
        each(pack, PreviewSpec.LAYER_PATHS, (i, in) -> out[i] = decodeCell(i, in));
        return out;
    }

    /**
     * The given textures of a zip or folder pack at full size, keyed by their path relative to {@link ModelSpec#ROOT}.
     * Paths under {@code items/} have animation strips cut to their first frame; others are returned as decoded.
     * Missing or unreadable files are left out of the map.
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
            Log.LOG.debug("packview: preview read failed for {}: {}", pack.getName(), e.toString());
        }
    }

    private static BufferedImage decode(InputStream in) {
        try {
            return ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Decodes only the part of a texture its preview cell shows (the heart's rectangle of icons.png, else the top
     * square frame) with power-of-two source subsampling down to no less than {@link PreviewSpec#MAX_CELL}, so a 4k
     * texture costs a 64 px image instead of 64 MB of pixels. Null when unreadable or too small to hold the heart.
     */
    private static BufferedImage decodeCell(int index, InputStream in) {
        try (ImageInputStream iis = new MemoryCacheImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                // ImageIO.read asks the same reader registry, so it would find nothing either
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                Rectangle region = cellRegion(index, reader.getWidth(0), reader.getHeight(0));
                if (region == null) {
                    return null;
                }
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceRegion(region);
                int factor = subsampling(region.width, PreviewSpec.MAX_CELL);
                if (factor > 1) {
                    param.setSourceSubsampling(factor, factor, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The source rectangle a cell shows: {@link PreviewSpec#heartCrop} for the heart, else the top square frame as
     * {@link PreviewSpec#firstFrame} takes it. Null when icons.png is too small to hold the heart.
     */
    private static Rectangle cellRegion(int index, int width, int height) {
        if (index != PreviewSpec.HEART) {
            return new Rectangle(0, 0, width, Math.min(width, height));
        }
        int[] r = PreviewSpec.heartCrop(width);
        if (r[2] <= 0 || r[3] <= 0 || r[0] + r[2] > width || r[1] + r[3] > height) {
            return null;
        }
        return new Rectangle(r[0], r[1], r[2], r[3]);
    }

    /**
     * The largest power-of-two subsampling that keeps {@code width} pixels at least {@code target} wide; 1 when the
     * image is under twice the target, so nothing is ever upscaled.
     */
    static int subsampling(int width, int target) {
        return target <= 0 ? 1 : Integer.highestOneBit(Math.max(1, width / target));
    }
}
