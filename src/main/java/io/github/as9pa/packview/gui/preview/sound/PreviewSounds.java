package io.github.as9pa.packview.gui.preview.sound;

import io.github.as9pa.packview.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Plays the preview's sounds from the chosen pack, falling back to vanilla one variant at a time. Reads the pack with
 * its own ZipFile or file reads, like PackTextureReader, and keeps the bytes for the session, so a zip is opened once
 * per version of the file (path and modified time). Remaps in a pack's sounds.json are ignored. No Minecraft
 * classes; not thread-safe, so call it from one thread.
 */
public final class PreviewSounds {
    /** Packs whose bytes stay cached; past this the least recently played one is dropped and its clips unloaded. */
    static final int MAX_PACKS = 8;

    private static final String VANILLA_PREFIX = "po_vanilla_";
    /** Every sound path the preview can play, read in one pass when a pack is first played. */
    private static final List<String> ALL_PATHS = allPaths();

    /** Where clips go to be heard; the playback agent supplies an implementation. */
    public interface Output {
        void play(String id, byte[] ogg, float volume, float pitch);

        void unload(String id);
    }

    private final Output out;
    private final Function<String, byte[]> vanilla;
    private final Random rnd;
    /** Packs read this session by path and modified time, least recently played first. */
    private final Map<String, PackSounds> packs = new LinkedHashMap<>(16, 0.75f, true);
    /** Vanilla clips by path; a null value marks a path the default pack lacks. */
    private final Map<String, SoundClip> vanillaClips = new HashMap<>();
    private final Set<String> vanillaPlayed = new LinkedHashSet<>();
    private boolean enabled = true;
    private File pack;
    private String packKey;
    /** Zip files opened so far, for tests. */
    int zipOpens;

    /** {@code vanilla} reads a pack-relative path from the default resource pack, null when absent. */
    public PreviewSounds(Output out, Function<String, byte[]> vanilla, Random rnd) {
        this.out = out;
        this.vanilla = vanilla;
        this.rnd = rnd;
    }

    /** The pack whose sounds play: its zip file or folder; null plays vanilla sounds. */
    public void setPack(File pack) {
        this.pack = pack;
        this.packKey = pack == null ? null : pack.getAbsolutePath() + '|' + pack.lastModified();
    }

    /**
     * Picks a random variant of s; reads it from the current pack, else vanilla; caches the bytes; calls out.play.
     * No-op when neither has it or when disabled.
     */
    public void play(PreviewSound s, float volume, float pitch) {
        if (!enabled) {
            return;
        }
        List<String> paths = s.paths();
        String path = paths.get(rnd.nextInt(paths.size()));
        PackSounds current = current();
        SoundClip clip = current == null ? null : current.clips.get(path);
        Set<String> played = current == null ? null : current.played;
        if (clip == null) {
            clip = vanillaClip(path);
            played = vanillaPlayed;
        }
        if (clip == null) {
            return;
        }
        played.add(clip.id);
        out.play(clip.id, clip.ogg, volume, pitch);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Unloads every clip it played (out.unload for each id) and clears the cache. */
    public void close() {
        for (PackSounds p : packs.values()) {
            unloadAll(p.played);
        }
        unloadAll(vanillaPlayed);
        packs.clear();
        vanillaClips.clear();
    }

    /** The current pack's sounds, read on first use; null when no pack is set. */
    private PackSounds current() {
        if (pack == null) {
            return null;
        }
        PackSounds p = packs.get(packKey);
        if (p == null) {
            p = new PackSounds("po_" + Long.toHexString(fnv64(packKey)) + "_");
            read(pack, p);
            packs.put(packKey, p);
            Iterator<PackSounds> oldest = packs.values().iterator();
            while (packs.size() > MAX_PACKS) {
                PackSounds dropped = oldest.next();
                oldest.remove();
                unloadAll(dropped.played);
            }
        }
        return p;
    }

    private SoundClip vanillaClip(String path) {
        if (!vanillaClips.containsKey(path)) {
            byte[] ogg = null;
            try {
                ogg = vanilla.apply(path);
            } catch (RuntimeException e) {
                Log.LOG.debug("packview: vanilla preview sound {} unreadable: {}", path, e.toString());
            }
            vanillaClips.put(path, ogg == null ? null : new SoundClip(VANILLA_PREFIX + suffix(path), ogg));
        }
        return vanillaClips.get(path);
    }

    private void unloadAll(Set<String> ids) {
        for (String id : ids) {
            out.unload(id);
        }
        ids.clear();
    }

    /** Reads every preview sound a folder or zip pack has into {@code into}; unreadable files are left out. */
    private void read(File pack, PackSounds into) {
        if (pack.isDirectory()) {
            for (String path : ALL_PATHS) {
                File f = new File(pack, path);
                if (f.isFile()) {
                    try {
                        into.put(path, Files.readAllBytes(f.toPath()));
                    } catch (IOException e) {
                        // vanilla plays instead
                    }
                }
            }
            return;
        }
        if (!pack.isFile()) {
            return;
        }
        zipOpens++;
        try (ZipFile zip = new ZipFile(pack)) {
            for (String path : ALL_PATHS) {
                ZipEntry entry = zip.getEntry(path);
                if (entry != null && !entry.isDirectory()) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        into.put(path, readAll(in));
                    } catch (IOException e) {
                        // vanilla plays instead
                    }
                }
            }
        } catch (IOException e) {
            Log.LOG.debug("packview: preview sound read failed for {}: {}", pack.getName(), e.toString());
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    /** "assets/minecraft/sounds/damage/hit1.ogg" becomes "damage_hit1.ogg". */
    private static String suffix(String path) {
        return path.substring(PreviewSound.ROOT.length()).replace('/', '_');
    }

    /** 64-bit FNV-1a, so two packs' ids collide far less often than with String.hashCode. */
    private static long fnv64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static List<String> allPaths() {
        List<String> all = new ArrayList<>();
        for (PreviewSound s : PreviewSound.values()) {
            all.addAll(s.paths());
        }
        return Collections.unmodifiableList(all);
    }

    /** One pack version's sounds: clips by path (a path it lacks has no entry) and the ids played from them. */
    private static final class PackSounds {
        final String prefix;
        final Map<String, SoundClip> clips = new HashMap<>();
        final Set<String> played = new LinkedHashSet<>();

        PackSounds(String prefix) {
            this.prefix = prefix;
        }

        void put(String path, byte[] ogg) {
            clips.put(path, new SoundClip(prefix + suffix(path), ogg));
        }
    }
}
