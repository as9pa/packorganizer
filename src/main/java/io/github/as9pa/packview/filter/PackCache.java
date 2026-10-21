package io.github.as9pa.packview.filter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.as9pa.packview.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * On-disk memory of scanned pack resolutions and colors, keyed by file name, size and modification time.
 * Thread-safe; loaded lazily on first use; never throws into the caller.
 */
public final class PackCache {
    public static final int FORMAT_VERSION = 5;

    private final File file;
    private final Map<String, Entry> entries = new HashMap<>();
    private boolean loaded;
    private boolean dirty;

    public PackCache(File file) {
        this.file = file;
    }

    public static String key(File pack) {
        return pack.getName() + ":" + pack.length() + ":" + pack.lastModified();
    }

    /** One pack's cached scan results. */
    public static final class Entry {
        public final PackResolution resolution;
        public final PackColors colors;

        public Entry(PackResolution resolution, PackColors colors) {
            this.resolution = resolution;
            this.colors = colors;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }
            Entry other = (Entry) o;
            return resolution.equals(other.resolution) && colors.equals(other.colors);
        }

        @Override
        public int hashCode() {
            return 31 * resolution.hashCode() + colors.hashCode();
        }
    }

    public synchronized Entry lookupEntry(String key) {
        ensureLoaded();
        return entries.get(key);
    }

    /** The cached resolution alone, or null on a miss. */
    public synchronized PackResolution lookup(String key) {
        Entry e = lookupEntry(key);
        return e == null ? null : e.resolution;
    }

    /** Stores a resolution with no colors (neither sword nor armor overridden). */
    public synchronized void put(String key, PackResolution resolution) {
        put(key, resolution, PackColors.NONE);
    }

    public synchronized void put(String key, PackResolution resolution, PackColors colors) {
        ensureLoaded();
        Entry entry = new Entry(resolution, colors);
        Entry previous = entries.put(key, entry);
        if (!entry.equals(previous)) {
            dirty = true;
        }
    }

    public synchronized int size() {
        ensureLoaded();
        return entries.size();
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    /** Writes the file if anything changed, via a temp file and rename. True when a write happened. */
    public synchronized boolean flush() {
        if (!dirty) {
            return false;
        }
        JsonObject packs = new JsonObject();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            PackResolution r = e.getValue().resolution;
            PackColors c = e.getValue().colors;
            JsonObject o = new JsonObject();
            o.addProperty("res", r.res);
            o.addProperty("kind", r.kind.name());
            if (r.stated > 0) {
                o.addProperty("stated", r.stated);
            }
            if (c.sword != null) {
                o.add("sword", c.sword.toJson());
            }
            if (c.armor != null) {
                o.add("armor", c.armor.toJson());
            }
            packs.add(e.getKey(), o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.add("packs", packs);

        File tmp = new File(file.getPath() + ".tmp");
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            return true;
        } catch (IOException | RuntimeException e) {
            Log.LOG.warn("packview: could not write {}: {}", file, e.toString());
            tmp.delete();
            return false;
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!file.isFile()) {
            Log.LOG.info("packview: no cache at {}, starting empty", file);
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(r).getAsJsonObject();
            JsonElement version = root.get("version");
            if (version == null || version.getAsInt() != FORMAT_VERSION) {
                Log.LOG.info("packview: ignoring {} (unsupported format)", file);
                return;
            }
            JsonObject packs = root.getAsJsonObject("packs");
            if (packs == null) {
                return;
            }
            int skipped = 0;
            for (Map.Entry<String, JsonElement> e : packs.entrySet()) {
                // One malformed entry must cost only itself, not the rest of the file.
                try {
                    entries.put(e.getKey(), read(e.getValue()));
                } catch (RuntimeException bad) {
                    skipped++;
                }
            }
            if (skipped > 0) {
                Log.LOG.info("packview: skipped {} malformed entries in {}", skipped, file);
            }
            Log.LOG.info("packview: loaded {} cached pack resolutions", entries.size());
        } catch (IOException | RuntimeException e) {
            entries.clear();
            Log.LOG.info("packview: ignoring unreadable {}: {}", file, e.toString());
        }
    }

    /** Throws a RuntimeException when the entry is not one the caller can use. */
    private static Entry read(JsonElement element) {
        JsonObject o = element.getAsJsonObject();
        return new Entry(readResolution(o), new PackColors(readFeatures(o, "sword"), readFeatures(o, "armor")));
    }

    /** Absent means the pack does not override that texture; present but malformed rejects the entry. */
    private static ColorFeatures readFeatures(JsonObject o, String name) {
        JsonElement element = o.get(name);
        if (element == null) {
            return null;
        }
        ColorFeatures f = ColorFeatures.fromJson(element);
        if (f == null) {
            throw new IllegalArgumentException("malformed " + name);
        }
        return f;
    }

    private static PackResolution readResolution(JsonObject o) {
        PackResolution.Kind kind = PackResolution.Kind.valueOf(o.get("kind").getAsString());
        if (kind == PackResolution.Kind.TEXTURES) {
            int res = o.get("res").getAsInt();
            JsonElement stated = o.get("stated");
            return stated == null ? PackResolution.textures(res) : PackResolution.stated(stated.getAsInt());
        }
        return kind == PackResolution.Kind.OVERLAY ? PackResolution.OVERLAY : PackResolution.UNKNOWN;
    }
}
