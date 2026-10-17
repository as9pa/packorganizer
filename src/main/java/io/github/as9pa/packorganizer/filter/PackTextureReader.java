package io.github.as9pa.packorganizer.filter;

import io.github.as9pa.packorganizer.Log;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        BufferedImage[] out = new BufferedImage[PreviewSpec.CELLS];
        if (pack.isDirectory()) {
            for (int i = 0; i < PreviewSpec.CELLS; i++) {
                File f = new File(pack, PreviewSpec.PATHS.get(i));
                if (f.isFile()) {
                    try (InputStream in = new FileInputStream(f)) {
                        out[i] = prepare(i, decode(in));
                    } catch (IOException e) {
                        // leave the cell empty
                    }
                }
            }
            return out;
        }
        if (!pack.isFile()) {
            return out;
        }
        try (ZipFile zip = new ZipFile(pack)) {
            for (int i = 0; i < PreviewSpec.CELLS; i++) {
                ZipEntry entry = zip.getEntry(PreviewSpec.PATHS.get(i));
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        out[i] = prepare(i, decode(in));
                    } catch (IOException e) {
                        // leave the cell empty
                    }
                }
            }
        } catch (IOException e) {
            Log.LOG.debug("packorganizer: preview read failed for {}: {}", pack.getName(), e.toString());
        }
        return out;
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
