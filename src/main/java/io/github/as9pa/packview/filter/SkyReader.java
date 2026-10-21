package io.github.as9pa.packview.filter;

import io.github.as9pa.packview.Log;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Reads a pack's MCPatcher or OptiFine custom sky layers with its own ZipFile or file reads, like
 * {@link PackTextureReader}. No Minecraft classes.
 */
public final class SkyReader {
    private static final String ROOT = "assets/minecraft/";
    /** Folders relative to {@link #ROOT}, in order of preference. */
    private static final String[] FOLDERS = {"mcpatcher/sky/world0", "optifine/sky/world0"};
    private static final Pattern FILE = Pattern.compile("sky(\\d+)\\.properties");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private SkyReader() {}

    /**
     * The layers of the first sky folder that has any, sorted by the N of skyN.properties. Files with unreadable
     * times or a missing image are skipped. Empty for a pack without a custom sky or one that cannot be read.
     */
    public static List<SkyLayer> layers(File pack) {
        if (pack.isDirectory()) {
            for (String folder : FOLDERS) {
                List<SkyLayer> out = folderLayers(pack, folder);
                if (!out.isEmpty()) {
                    return out;
                }
            }
            return Collections.emptyList();
        }
        if (!pack.isFile()) {
            return Collections.emptyList();
        }
        try (ZipFile zip = new ZipFile(pack)) {
            for (String folder : FOLDERS) {
                List<SkyLayer> out = zipLayers(zip, folder);
                if (!out.isEmpty()) {
                    return out;
                }
            }
        } catch (IOException e) {
            Log.LOG.debug("packview: sky read failed for {}: {}", pack.getName(), e.toString());
        }
        return Collections.emptyList();
    }

    /** One skyN.properties file whose N is unknown; null when the times are unreadable or there is no source. */
    public static SkyLayer parse(String propertiesText, String folder) {
        return parse(propertiesText, folder, -1);
    }

    /**
     * One skyN.properties file in folder (relative to assets/minecraft/). Null when a time is missing or unreadable
     * or the source cannot be resolved.
     */
    public static SkyLayer parse(String propertiesText, String folder, int n) {
        Properties p = new Properties();
        try {
            String text = propertiesText.startsWith(BOM) ? propertiesText.substring(1) : propertiesText;
            p.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        int startFadeIn = time(p.getProperty("startFadeIn"));
        int endFadeIn = time(p.getProperty("endFadeIn"));
        int endFadeOut = time(p.getProperty("endFadeOut"));
        if (startFadeIn < 0 || endFadeIn < 0 || endFadeOut < 0) {
            return null;
        }
        int startFadeOut;
        String fadeOut = p.getProperty("startFadeOut");
        if (fadeOut == null) {
            int fadeIn = Math.floorMod(endFadeIn - startFadeIn, SkyLayer.MINUTES_PER_DAY);
            startFadeOut = Math.floorMod(endFadeOut - fadeIn, SkyLayer.MINUTES_PER_DAY);
        } else {
            startFadeOut = time(fadeOut);
            if (startFadeOut < 0) {
                return null;
            }
        }
        String source = source(p.getProperty("source"), folder, n);
        if (source == null) {
            return null;
        }
        String blend = p.getProperty("blend", "add").trim().toLowerCase(Locale.ROOT);
        if (blend.isEmpty()) {
            blend = "add";
        }
        boolean rotate = !"false".equalsIgnoreCase(p.getProperty("rotate", "true").trim());
        return new SkyLayer(source, startFadeIn, endFadeIn, endFadeOut, startFadeOut, blend, rotate,
                speed(p.getProperty("speed")));
    }

    /**
     * A layer's image, subsampled and then smoothly scaled down to at most maxWidth wide (no limit when maxWidth is
     * 0 or less). Null when the file is missing or unreadable.
     */
    public static BufferedImage image(File pack, String source, int maxWidth) {
        String path = entryPath(source);
        if (pack.isDirectory()) {
            File f = new File(pack, path);
            if (!f.isFile()) {
                return null;
            }
            try (InputStream in = new FileInputStream(f)) {
                return decode(in, maxWidth);
            } catch (IOException e) {
                return null;
            }
        }
        if (!pack.isFile()) {
            return null;
        }
        try (ZipFile zip = new ZipFile(pack)) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return decode(in, maxWidth);
            }
        } catch (IOException e) {
            Log.LOG.debug("packview: sky image read failed for {}: {}", pack.getName(), e.toString());
            return null;
        }
    }

    private static List<SkyLayer> folderLayers(File pack, String folder) {
        File[] files = new File(pack, ROOT + folder).listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        Map<Integer, File> byN = new TreeMap<>();
        for (File f : files) {
            Matcher m = FILE.matcher(f.getName());
            if (f.isFile() && m.matches()) {
                Integer n = number(m.group(1));
                if (n != null) {
                    byN.put(n, f);
                }
            }
        }
        List<SkyLayer> out = new ArrayList<>();
        for (Map.Entry<Integer, File> e : byN.entrySet()) {
            try (InputStream in = new FileInputStream(e.getValue())) {
                SkyLayer layer = parse(text(in), folder, e.getKey());
                if (layer != null && new File(pack, entryPath(layer.source)).isFile()) {
                    out.add(layer);
                }
            } catch (IOException ex) {
                // skip the layer
            }
        }
        return out;
    }

    private static List<SkyLayer> zipLayers(ZipFile zip, String folder) {
        String prefix = ROOT + folder + "/";
        Map<Integer, ZipEntry> byN = new TreeMap<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(prefix)) {
                continue;
            }
            Matcher m = FILE.matcher(name.substring(prefix.length()));
            if (m.matches()) {
                Integer n = number(m.group(1));
                if (n != null) {
                    byN.put(n, entry);
                }
            }
        }
        List<SkyLayer> out = new ArrayList<>();
        for (Map.Entry<Integer, ZipEntry> e : byN.entrySet()) {
            try (InputStream in = zip.getInputStream(e.getValue())) {
                SkyLayer layer = parse(text(in), folder, e.getKey());
                if (layer != null && zip.getEntry(entryPath(layer.source)) != null) {
                    out.add(layer);
                }
            } catch (IOException ex) {
                // skip the layer
            }
        }
        return out;
    }

    /**
     * Resolves a source value to a path relative to assets/minecraft/ (or from the pack root when it names another
     * namespace under assets/). Missing means skyN.png in the folder; "./x" and a bare name are in the folder;
     * "~/x" is in optifine/; "minecraft:" and "assets/minecraft/" prefixes are dropped.
     */
    private static String source(String value, String folder, int n) {
        String s = value == null ? "" : value.trim().replace('\\', '/');
        if (s.isEmpty()) {
            return n < 0 ? null : folder + "/sky" + n + ".png";
        }
        if (s.startsWith("minecraft:")) {
            s = s.substring("minecraft:".length());
        }
        if (s.startsWith("./")) {
            s = folder + "/" + s.substring(2);
        } else if (s.startsWith("~/")) {
            s = "optifine/" + s.substring(2);
        } else {
            while (s.startsWith("/")) {
                s = s.substring(1);
            }
            if (s.startsWith(ROOT)) {
                s = s.substring(ROOT.length());
            } else if (s.indexOf('/') < 0) {
                s = folder + "/" + s;
            }
        }
        if (s.isEmpty() || s.endsWith("/") || ("/" + s + "/").contains("/../")) {
            return null;
        }
        return s;
    }

    private static String entryPath(String source) {
        return source.startsWith("assets/") ? source : ROOT + source;
    }

    /** Minutes of the day for "h:mm", or -1 when missing or unreadable. 24:00 is midnight. */
    private static int time(String value) {
        if (value == null) {
            return -1;
        }
        Matcher m = TIME.matcher(value.trim());
        if (!m.matches()) {
            return -1;
        }
        int hours = Integer.parseInt(m.group(1));
        int minutes = Integer.parseInt(m.group(2));
        if (hours > 24 || minutes > 59 || (hours == 24 && minutes > 0)) {
            return -1;
        }
        return (hours * 60 + minutes) % SkyLayer.MINUTES_PER_DAY;
    }

    private static float speed(String value) {
        if (value == null) {
            return 1f;
        }
        try {
            float f = Float.parseFloat(value.trim());
            return Float.isNaN(f) || Float.isInfinite(f) ? 1f : f;
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    private static Integer number(String digits) {
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static BufferedImage decode(InputStream in, int maxWidth) {
        try (ImageInputStream iis = new MemoryCacheImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                ImageReadParam param = reader.getDefaultReadParam();
                int factor = maxWidth > 0 ? Math.max(1, width / maxWidth) : 1;
                if (factor > 1) {
                    param.setSourceSubsampling(factor, factor, 0, 0);
                }
                return shrink(reader.read(0, param), maxWidth);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static BufferedImage shrink(BufferedImage img, int maxWidth) {
        if (img == null || maxWidth <= 0 || img.getWidth() <= maxWidth) {
            return img;
        }
        int h = Math.max(1, (int) Math.round(img.getHeight() * (double) maxWidth / img.getWidth()));
        BufferedImage out = new BufferedImage(maxWidth, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, maxWidth, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
