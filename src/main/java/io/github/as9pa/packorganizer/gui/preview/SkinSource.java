package io.github.as9pa.packorganizer.gui.preview;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import io.github.as9pa.packorganizer.Log;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

/**
 * The player's own skin and cape for the preview. {@link #get()} never waits: it gives Steve or Alex (from the UUID)
 * and no cape until the real ones arrive. The first call starts one background attempt per session on a daemon
 * thread: the Mojang profile (skin, slim arms, cape, loaded through {@link SkinManager}) and the OptiFine cape, which
 * wins over the Mojang one. Offline or on any error the fallback stays and nothing is retried. Show cape is up to the
 * caller. Logs never carry the player name or UUID.
 */
public final class SkinSource {
    private static final String OPTIFINE_CAPES = "http://s.optifine.net/capes/";
    private static final int TIMEOUT_MS = 5000;

    /** Client thread only. */
    private static boolean started;
    private static ResourceLocation fallbackSkin;
    private static boolean fallbackSlim;
    private static SkinTextures last;

    /** Set by the {@link SkinManager} callback, on the client thread or its download thread; {@code slim} first. */
    private static volatile boolean slim;
    private static volatile ResourceLocation skin;
    private static volatile ResourceLocation mojangCape;
    /** Our own GL texture, uploaded on the client thread; never deleted, it lasts the session. */
    private static int optifineCape;

    private SkinSource() {}

    /** Client thread only. GL ids are looked up each call since a resource reload can replace them. */
    public static SkinTextures get() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!started) {
            started = true;
            start(mc);
        }
        TextureManager textures = mc.getTextureManager();
        ResourceLocation loaded = skin;
        int skinId = loaded == null ? 0 : glId(textures, loaded);
        boolean isSlim = slim;
        if (skinId == 0) {
            if (textures.getTexture(fallbackSkin) == null) {
                // What bindTexture does for a texture it has not seen; a missing one becomes the missing texture.
                textures.loadTexture(fallbackSkin, new SimpleTexture(fallbackSkin));
            }
            skinId = glId(textures, fallbackSkin);
            isSlim = fallbackSlim;
        }
        ResourceLocation cape = mojangCape;
        int capeId = optifineCape != 0 ? optifineCape : cape == null ? 0 : glId(textures, cape);
        SkinTextures prev = last;
        if (prev == null || prev.skin != skinId || prev.slim != isSlim || prev.cape != capeId) {
            last = new SkinTextures(skinId, isSlim, capeId);
        }
        return last;
    }

    private static void start(Minecraft mc) {
        GameProfile profile = mc.getSession().getProfile();
        UUID id = profile.getId();
        fallbackSkin = id == null ? DefaultPlayerSkin.getDefaultSkinLegacy() : DefaultPlayerSkin.getDefaultSkin(id);
        fallbackSlim = id != null && "slim".equals(DefaultPlayerSkin.getSkinType(id));
        MinecraftSessionService sessions = mc.getSessionService();
        Proxy set = mc.getProxy();
        Proxy proxy = set == null ? Proxy.NO_PROXY : set;
        Thread t = new Thread(() -> {
            loadProfile(mc, sessions, profile);
            loadOptifineCape(mc, proxy, profile.getName());
        }, "packorganizer-skin");
        t.setDaemon(true);
        t.start();
    }

    /** The GL id of a texture the {@link TextureManager} holds, or 0. A downloaded skin uploads its image here. */
    private static int glId(TextureManager textures, ResourceLocation loc) {
        try {
            ITextureObject tex = textures.getTexture(loc);
            return tex == null ? 0 : Math.max(0, tex.getGlTextureId());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Skin thread: fills the profile, then hands its textures to {@link SkinManager} on the client thread. */
    private static void loadProfile(Minecraft mc, MinecraftSessionService sessions, GameProfile profile) {
        try {
            if (profile.getId() == null) {
                Log.LOG.debug("packorganizer: no profile id, keeping the default skin");
                return;
            }
            GameProfile filled = sessions.fillProfileProperties(profile, false);
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> found = sessions.getTextures(filled, false);
            MinecraftProfileTexture skinTex = found.get(MinecraftProfileTexture.Type.SKIN);
            MinecraftProfileTexture capeTex = found.get(MinecraftProfileTexture.Type.CAPE);
            if (skinTex == null && capeTex == null) {
                Log.LOG.debug("packorganizer: no profile textures, keeping the default skin");
                return;
            }
            boolean isSlim = CapeImages.isSlim(texturesJson(filled));
            mc.addScheduledTask(() -> request(mc.getSkinManager(), skinTex, capeTex, isSlim));
        } catch (RuntimeException e) {
            Log.LOG.debug("packorganizer: skin lookup failed: {}", e.getClass().getSimpleName());
        }
    }

    /** The decoded {@code textures} profile property, or null. */
    private static String texturesJson(GameProfile profile) {
        Property p = Iterables.getFirst(profile.getProperties().get("textures"), null);
        return p == null ? null : new String(Base64.getDecoder().decode(p.getValue()), StandardCharsets.UTF_8);
    }

    /** Client thread: {@link SkinManager#loadSkin} uploads its placeholder, so it cannot run on the skin thread. */
    private static void request(SkinManager skins, MinecraftProfileTexture skinTex, MinecraftProfileTexture capeTex,
            boolean isSlim) {
        try {
            if (skinTex != null) {
                skins.loadSkin(skinTex, MinecraftProfileTexture.Type.SKIN, (type, loc, tex) -> {
                    slim = isSlim;
                    skin = loc;
                });
            }
            if (capeTex != null) {
                skins.loadSkin(capeTex, MinecraftProfileTexture.Type.CAPE, (type, loc, tex) -> mojangCape = loc);
            }
        } catch (RuntimeException e) {
            Log.LOG.debug("packorganizer: skin request failed: {}", e.getClass().getSimpleName());
        }
    }

    /** Skin thread: downloads the OptiFine cape and uploads it on the client thread. The URL holds the name. */
    private static void loadOptifineCape(Minecraft mc, Proxy proxy, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(OPTIFINE_CAPES + name + ".png").openConnection(proxy);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                Log.LOG.debug("packorganizer: no OptiFine cape (HTTP {})", status);
                return;
            }
            BufferedImage img;
            try (InputStream in = conn.getInputStream()) {
                img = ImageIO.read(in);
            }
            if (img == null) {
                Log.LOG.debug("packorganizer: OptiFine cape is not an image");
                return;
            }
            BufferedImage canvas = CapeImages.optifineCanvas(img);
            mc.addScheduledTask(() -> uploadOptifineCape(canvas));
        } catch (IOException | RuntimeException e) {
            // The message can carry the URL, and with it the name.
            Log.LOG.debug("packorganizer: OptiFine cape fetch failed: {}", e.getClass().getSimpleName());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Client thread. */
    private static void uploadOptifineCape(BufferedImage img) {
        int id = TextureUtil.glGenTextures();
        try {
            TextureUtil.uploadTextureImageAllocate(id, img, false, false);
            optifineCape = id;
        } catch (RuntimeException e) {
            Log.LOG.debug("packorganizer: OptiFine cape upload failed: {}", e.toString());
            TextureUtil.deleteTexture(id);
        }
    }
}
