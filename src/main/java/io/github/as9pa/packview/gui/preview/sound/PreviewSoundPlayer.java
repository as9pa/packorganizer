package io.github.as9pa.packview.gui.preview.sound;

import io.github.as9pa.packview.Log;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;

/**
 * Plays an in-memory .ogg through Minecraft's running sound engine, so a preview can sound a clip from a pack that is
 * not active. Mirrors {@link SoundManager#playSound}: a new paulscode source from a URL whose handler serves the
 * bytes, then pitch, volume and play. The engine is reached by reflection (MCP and SRG names); if that fails one WARN
 * is logged and every play is a no-op. Each play is a fresh temporary, non-positional source that the engine drops
 * after it ends. The engine caches the decoded clip under its id, so ids must be unique per pack and file.
 */
public final class PreviewSoundPlayer {
    private static final String PROTOCOL = "packview";
    private static final String LOG_PROPERTY = "packview.logSounds";
    private static final String SOURCE_PREFIX = "po";
    /** Vanilla's distOrRoll for a sound at volume 1; ignored with no attenuation, passed for parity. */
    private static final float DIST_OR_ROLL = 16f;

    /** Client thread only. Fields are looked up once; the engine is read each call since a reload replaces it. */
    private static boolean looked;
    private static Field sndManager;
    private static Field sndSystem;
    private static Field loaded;
    private static long plays;

    private PreviewSoundPlayer() {}

    /**
     * Plays ogg once, non-positional (attenuation none), at volume * the Players sound slider, at pitch. Returns false
     * and does nothing when the sound engine is not loaded, the clip is empty, or the resulting volume is 0. Client
     * thread only.
     */
    public static boolean play(String id, byte[] ogg, float volume, float pitch) {
        if (id == null || ogg == null || ogg.length == 0) return false;
        Minecraft mc = Minecraft.getMinecraft();
        SoundSystem system = system(mc);
        if (system == null) return false;
        float v = volume(volume, mc.gameSettings.getSoundLevel(SoundCategory.PLAYERS));
        if (v <= 0f) return false;
        float p = pitch(pitch);
        try {
            // Vanilla skips every sound when the master volume is zero.
            if (system.getMasterVolume() <= 0f) return false;
            String source = sourceName(++plays);
            system.newSource(false, source, url(id, ogg), id, false, 0f, 0f, 0f,
                    SoundSystemConfig.ATTENUATION_NONE, DIST_OR_ROLL);
            system.setTemporary(source, true);
            system.setPitch(source, p);
            system.setVolume(source, v);
            system.play(source);
        } catch (MalformedURLException | RuntimeException e) {
            disable(e);
            return false;
        }
        if ("true".equals(System.getProperty(LOG_PROPERTY))) {
            Log.LOG.info("Preview sound {} volume {} pitch {}", id, v, p);
        }
        return true;
    }

    /** Frees the engine's decoded buffer for id (call when the clip will not be played again). */
    public static void unload(String id) {
        if (id == null) return;
        SoundSystem system = system(Minecraft.getMinecraft());
        if (system == null) return;
        try {
            system.unloadSound(id);
        } catch (RuntimeException e) {
            disable(e);
        }
    }

    /** The running engine, or null when it is not loaded yet, is reloading, or could not be reached. */
    private static SoundSystem system(Minecraft mc) {
        if (!looked) {
            looked = true;
            try {
                sndManager = ReflectionHelper.findField(SoundHandler.class, "sndManager", "field_147694_f");
                sndSystem = ReflectionHelper.findField(SoundManager.class, "sndSystem", "field_148620_e");
                loaded = ReflectionHelper.findField(SoundManager.class, "loaded", "field_148617_f");
            } catch (RuntimeException e) {
                disable(e);
            }
        }
        if (loaded == null) return null;
        SoundHandler handler = mc.getSoundHandler();
        if (handler == null) return null;
        try {
            Object manager = sndManager.get(handler);
            if (manager == null || !loaded.getBoolean(manager)) return null;
            Object system = sndSystem.get(manager);
            return system instanceof SoundSystem ? (SoundSystem) system : null;
        } catch (IllegalAccessException | RuntimeException e) {
            disable(e);
            return null;
        }
    }

    /** Turns preview sounds off for the session; logs once since every later call returns before reaching here. */
    private static void disable(Exception e) {
        sndManager = null;
        sndSystem = null;
        loaded = null;
        Log.LOG.warn("Preview sounds are off: cannot use the sound engine", e);
    }

    /** Vanilla's pitch range ({@code SoundManager.getNormalizedPitch}); NaN plays at normal pitch. */
    static float pitch(float pitch) {
        if (Float.isNaN(pitch)) return 1f;
        return Math.max(0.5f, Math.min(2f, pitch));
    }

    /** The source volume: volume times the Players slider, in 0..1. The engine applies the master slider itself. */
    static float volume(float volume, float playersLevel) {
        float v = volume * playersLevel;
        if (Float.isNaN(v)) return 0f;
        return Math.max(0f, Math.min(1f, v));
    }

    /** A source name that cannot collide with vanilla's, which are UUID strings. */
    static String sourceName(long n) {
        return SOURCE_PREFIX + n;
    }

    /**
     * A URL that serves exactly ogg, like {@code SoundManager.getURLForSoundResource} serves a resource. The handler
     * is passed in, so no global protocol handler is registered, and the id is not parsed as a URL.
     */
    static URL url(String id, final byte[] ogg) throws MalformedURLException {
        return new URL(PROTOCOL, "", -1, id, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return new URLConnection(u) {
                    @Override
                    public void connect() {}

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(ogg);
                    }

                    @Override
                    public long getContentLengthLong() {
                        return ogg.length;
                    }
                };
            }
        });
    }
}
