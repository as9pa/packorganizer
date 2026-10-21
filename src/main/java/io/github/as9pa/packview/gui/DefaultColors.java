package io.github.as9pa.packview.gui;

import io.github.as9pa.packview.filter.ColorFeatures;
import io.github.as9pa.packview.filter.PackColors;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

/**
 * Sword and armor color features of Minecraft's default pack, read once on a background thread.
 * Packs that do not override a slot are scored with these. Same textures as ColorScanner reads.
 */
public final class DefaultColors {
    private static final String SWORD = "textures/items/diamond_sword.png";
    private static final String[] ARMOR = {"textures/models/armor/diamond_layer_1.png", "textures/models/armor/diamond_layer_2.png"};

    private static volatile PackColors colors;
    private static boolean started;

    private DefaultColors() {}

    /** The default pack's colors, or null until the one-time read has finished. Call on the client thread. */
    public static PackColors get() {
        if (!started) {
            started = true;
            IResourcePack defaultPack = Minecraft.getMinecraft().getResourcePackRepository().rprDefaultResourcePack;
            Thread t = new Thread(() -> colors = read(defaultPack), "packview-default-colors");
            t.setDaemon(true);
            t.start();
        }
        return colors;
    }

    private static PackColors read(IResourcePack defaultPack) {
        int[] sword = pixels(defaultPack, SWORD);
        List<int[]> found = new ArrayList<>();
        int total = 0;
        for (String path : ARMOR) {
            int[] px = pixels(defaultPack, path);
            if (px != null) {
                found.add(px);
                total += px.length;
            }
        }
        int[] armor = null;
        if (!found.isEmpty()) {
            armor = new int[total];
            int at = 0;
            for (int[] px : found) {
                System.arraycopy(px, 0, armor, at, px.length);
                at += px.length;
            }
        }
        return new PackColors(sword == null ? null : ColorFeatures.from(sword), armor == null ? null : ColorFeatures.from(armor));
    }

    /** ARGB pixels of the texture's top square, or null when the default pack lacks it. */
    private static int[] pixels(IResourcePack defaultPack, String path) {
        try (InputStream in = defaultPack.getInputStream(new ResourceLocation("minecraft", path))) {
            BufferedImage img = in == null ? null : ImageIO.read(in);
            if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
                return null;
            }
            int w = img.getWidth();
            int h = Math.min(img.getHeight(), w);
            return img.getRGB(0, 0, w, h, null, 0, w);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
