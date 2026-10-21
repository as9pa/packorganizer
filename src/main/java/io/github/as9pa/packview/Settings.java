package io.github.as9pa.packview;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * User settings, stored as config/packview/settings.json.
 * Loading never throws: a missing file, bad JSON or a missing or malformed key falls back to the default.
 */
public final class Settings {
    public static final String FILE_NAME = "settings.json";

    public boolean packPreviews = true;
    public boolean transparentBackground = false;
    public boolean packFolders = true;
    public boolean colorFilter = true;
    public boolean resolutionLabels = true;
    public boolean rememberOpenFolders = true;
    public Set<String> openFolders = new LinkedHashSet<>();
    public boolean playerPreview = true;
    public boolean skyBackdrop = true;
    public boolean skyCycle = true;
    public boolean previewHitTint = true;
    public boolean previewSounds = true;
    public boolean showCape = true;
    /** Preview armor from ARMORS, null for none. Not saved: each opening of the screen starts with no armor. */
    public String previewArmor = null;
    /** Preview item from ITEMS, or "none" for the empty hand. Not saved: each opening starts on the fist. */
    public String previewItem = "none";
    public boolean previewEnchanted = false;
    /** Pack list rows show item previews. Set by the toolbar's Previews toggle; not on the settings screen. */
    public boolean listPreviews = false;
    /** Starred packs, keyed by path relative to resourcepacks with "/" separators (nested packs included). */
    public Set<String> favorites = new LinkedHashSet<>();
    /** Bumped on every favorite toggle, so the pack list can tell its order is stale. Not saved. */
    private int favoritesRevision;

    public static final List<String> ARMORS = Collections.unmodifiableList(Arrays.asList(
            "leather", "chainmail", "iron", "gold", "diamond"));
    public static final List<String> ITEMS = Collections.unmodifiableList(Arrays.asList(
            "sword", "bow", "ender_pearl", "golden_apple", "splash_potion", "fishing_rod"));

    /** One ON/OFF toggle on the settings screen, bound to exactly one boolean field. */
    public static final class Switch {
        public final String label;
        private final Predicate<Settings> getter;
        private final BiConsumer<Settings, Boolean> setter;

        Switch(String label, Predicate<Settings> getter, BiConsumer<Settings, Boolean> setter) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
        }

        public boolean get(Settings s) {
            return getter.test(s);
        }

        public void set(Settings s, boolean v) {
            setter.accept(s, v);
        }
    }

    /** Settings screen order. Index is the button id. */
    public static final List<Switch> SWITCHES = Collections.unmodifiableList(Arrays.asList(
            new Switch("Pack previews", s -> s.packPreviews, (s, v) -> s.packPreviews = v),
            new Switch("Transparent background", s -> s.transparentBackground, (s, v) -> s.transparentBackground = v),
            new Switch("Pack folders", s -> s.packFolders, (s, v) -> s.packFolders = v),
            new Switch("Color filter", s -> s.colorFilter, (s, v) -> s.colorFilter = v),
            new Switch("Resolution labels", s -> s.resolutionLabels, (s, v) -> s.resolutionLabels = v),
            new Switch("Remember open folders", s -> s.rememberOpenFolders, (s, v) -> s.rememberOpenFolders = v),
            new Switch("Player preview", s -> s.playerPreview, (s, v) -> s.playerPreview = v),
            new Switch("Pack sky backdrop", s -> s.skyBackdrop, (s, v) -> s.skyBackdrop = v),
            new Switch("Sky day cycle", s -> s.skyCycle, (s, v) -> s.skyCycle = v),
            new Switch("Show cape", s -> s.showCape, (s, v) -> s.showCape = v),
            new Switch("Hit tint", s -> s.previewHitTint, (s, v) -> s.previewHitTint = v),
            new Switch("Preview sounds", s -> s.previewSounds, (s, v) -> s.previewSounds = v)));

    public boolean isFavorite(String key) {
        return key != null && favorites.contains(key);
    }

    /** Stars or unstars the pack, then saves when these are the loaded settings. Null or "" (no pack) is ignored. */
    public void toggleFavorite(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (!favorites.remove(key)) {
            favorites.add(key);
        }
        favoritesRevision++;
        synchronized (Settings.class) {
            if (this == current) {
                saveNow();
            }
        }
    }

    /** Changes whenever {@link #toggleFavorite} does; compare against a stored value to detect a toggle. */
    public int favoritesRevision() {
        return favoritesRevision;
    }

    private static Settings current;
    private static File currentFile;

    /** Reads configDir/settings.json once; later calls are no-ops. */
    public static synchronized void init(File configDir) {
        if (current != null) {
            return;
        }
        currentFile = new File(configDir, FILE_NAME);
        current = load(currentFile);
    }

    /** The loaded settings, or defaults when init has not run. */
    public static synchronized Settings get() {
        if (current == null) {
            current = new Settings();
        }
        return current;
    }

    /** Writes the current settings to the file init read from. No-op before init. */
    public static synchronized void saveNow() {
        if (current != null && currentFile != null) {
            current.save(currentFile);
        }
    }

    public static Settings load(File file) {
        Settings s = new Settings();
        if (!file.isFile()) {
            Log.LOG.info("packview: no settings at {}, using defaults", file);
            return s;
        }
        JsonObject root;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(r).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            Log.LOG.info("packview: ignoring unreadable {}: {}", file, e.toString());
            return s;
        }
        s.packPreviews = bool(root, "packPreviews", s.packPreviews);
        s.transparentBackground = bool(root, "transparentBackground", s.transparentBackground);
        s.packFolders = bool(root, "packFolders", s.packFolders);
        s.colorFilter = bool(root, "colorFilter", s.colorFilter);
        s.resolutionLabels = bool(root, "resolutionLabels", s.resolutionLabels);
        s.rememberOpenFolders = bool(root, "rememberOpenFolders", s.rememberOpenFolders);
        if (s.rememberOpenFolders) {
            JsonElement folders = root.get("openFolders");
            if (folders != null && folders.isJsonArray()) {
                for (JsonElement e : folders.getAsJsonArray()) {
                    // One bad element must cost only itself.
                    if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                        s.openFolders.add(e.getAsString());
                    }
                }
            }
        }
        s.playerPreview = bool(root, "playerPreview", s.playerPreview);
        s.skyBackdrop = bool(root, "skyBackdrop", s.skyBackdrop);
        s.skyCycle = bool(root, "skyCycle", s.skyCycle);
        s.previewHitTint = bool(root, "previewHitTint", s.previewHitTint);
        s.previewSounds = bool(root, "previewSounds", s.previewSounds);
        s.showCape = bool(root, "showCape", s.showCape);
        s.previewEnchanted = bool(root, "previewEnchanted", s.previewEnchanted);
        s.listPreviews = bool(root, "listPreviews", s.listPreviews);
        JsonElement favorites = root.get("favorites");
        if (favorites != null && favorites.isJsonArray()) {
            for (JsonElement e : favorites.getAsJsonArray()) {
                // One bad element must cost only itself.
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && !e.getAsString().isEmpty()) {
                    s.favorites.add(e.getAsString());
                }
            }
        }
        return s;
    }

    /** Writes via a temp file and rename. Never throws. */
    public void save(File file) {
        JsonObject root = new JsonObject();
        root.addProperty("packPreviews", packPreviews);
        root.addProperty("transparentBackground", transparentBackground);
        root.addProperty("packFolders", packFolders);
        root.addProperty("colorFilter", colorFilter);
        root.addProperty("resolutionLabels", resolutionLabels);
        root.addProperty("rememberOpenFolders", rememberOpenFolders);
        JsonArray folders = new JsonArray();
        if (rememberOpenFolders) {
            for (String f : openFolders) {
                folders.add(new JsonPrimitive(f));
            }
        }
        root.add("openFolders", folders);
        root.addProperty("playerPreview", playerPreview);
        root.addProperty("skyBackdrop", skyBackdrop);
        root.addProperty("skyCycle", skyCycle);
        root.addProperty("previewHitTint", previewHitTint);
        root.addProperty("previewSounds", previewSounds);
        root.addProperty("showCape", showCape);
        root.addProperty("previewEnchanted", previewEnchanted);
        root.addProperty("listPreviews", listPreviews);
        JsonArray favs = new JsonArray();
        for (String f : favorites) {
            favs.add(new JsonPrimitive(f));
        }
        root.add("favorites", favs);

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
        } catch (IOException | RuntimeException e) {
            Log.LOG.warn("packview: could not write {}: {}", file, e.toString());
            tmp.delete();
        }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        JsonElement e = root.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        return fallback;
    }
}
