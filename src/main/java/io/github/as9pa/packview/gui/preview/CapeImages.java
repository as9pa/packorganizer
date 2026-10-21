package io.github.as9pa.packview.gui.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;

/** Plain helpers for the preview's skin and cape: the OptiFine cape layout and the skin model. No Minecraft classes. */
public final class CapeImages {
    private static final int OPTIFINE_W = 46;
    private static final int OPTIFINE_H = 22;
    private static final int CANVAS_W = 64;
    private static final int CANVAS_H = 32;

    private CapeImages() {}

    /**
     * An OptiFine cape (46x22 or a whole multiple of it) placed top-left on a transparent 64x32 canvas of the same
     * multiple, so it maps like a vanilla cape. Any other size is returned as is.
     */
    public static BufferedImage optifineCanvas(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w % OPTIFINE_W != 0 || h % OPTIFINE_H != 0) {
            return src;
        }
        int scale = w / OPTIFINE_W;
        if (scale < 1 || h / OPTIFINE_H != scale) {
            return src;
        }
        BufferedImage out = new BufferedImage(CANVAS_W * scale, CANVAS_H * scale, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, src.getRGB(0, 0, w, h, null, 0, w), 0, w);
        return out;
    }

    /**
     * True when the decoded {@code textures} profile property says the skin uses the slim (Alex) arms:
     * {@code textures.SKIN.metadata.model} is {@code "slim"}. Missing or broken JSON is not slim.
     */
    public static boolean isSlim(String profileTexturesJson) {
        if (profileTexturesJson == null) {
            return false;
        }
        try {
            JsonObject meta = child(child(child(new JsonParser().parse(profileTexturesJson), "textures"), "SKIN"),
                    "metadata");
            JsonElement model = meta == null ? null : meta.get("model");
            return model != null && model.isJsonPrimitive() && "slim".equals(model.getAsString());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static JsonObject child(JsonElement parent, String key) {
        if (parent == null || !parent.isJsonObject()) {
            return null;
        }
        JsonElement value = parent.getAsJsonObject().get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
