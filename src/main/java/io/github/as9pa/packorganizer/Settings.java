package io.github.as9pa.packorganizer;

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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User settings, stored as config/packorganizer/settings.json.
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
            Log.LOG.info("packorganizer: no settings at {}, using defaults", file);
            return s;
        }
        JsonObject root;
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(r).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            Log.LOG.info("packorganizer: ignoring unreadable {}: {}", file, e.toString());
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
            Log.LOG.warn("packorganizer: could not write {}: {}", file, e.toString());
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
