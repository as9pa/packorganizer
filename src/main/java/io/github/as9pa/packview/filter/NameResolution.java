package io.github.as9pa.packview.filter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a resolution the pack states in its name or description, like "[32x]", "Faithful 64x" or "HP2019[32]".
 * Runs on a background thread; must not touch Minecraft classes.
 */
public final class NameResolution {
    private static final Pattern COLOUR_CODE = Pattern.compile("\u00a7.", Pattern.DOTALL);
    /** A whole number of a known size, then "x"; "1.7-1.8", "16xx" and "x16" stay out. */
    private static final Pattern TOKEN = Pattern.compile("(?<![0-9])(8|16|32|64|128|256|512)\\s*x(?![a-z0-9])", Pattern.CASE_INSENSITIVE);
    /** A known size in square brackets with no "x", like "[32]" or "[ 64 ]"; only used when no "Nx" token is present. */
    private static final Pattern BRACKETED = Pattern.compile("\\[\\s*(8|16|32|64|128|256|512)\\s*\\]");
    /** The whole word "overlay" or "overlays"; "Overlayer" stays out. */
    private static final Pattern OVERLAY = Pattern.compile("(?<![a-z])overlays?(?![a-z])", Pattern.CASE_INSENSITIVE);

    private NameResolution() {}

    /** The first stated resolution in the text, or 0 when there is none. */
    public static int find(String text) {
        if (text == null) {
            return 0;
        }
        String plain = COLOUR_CODE.matcher(text).replaceAll("");
        Matcher m = TOKEN.matcher(plain);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        m = BRACKETED.matcher(plain);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** True when the colour-stripped text names itself an overlay. */
    public static boolean isOverlay(String text) {
        return text != null && OVERLAY.matcher(COLOUR_CODE.matcher(text).replaceAll("")).find();
    }

    /** pack.description from a pack.mcmeta stream: a text component comes back as raw JSON. Never throws; "" on failure. */
    public static String description(InputStream mcmeta) {
        try {
            JsonObject pack = new JsonParser().parse(new InputStreamReader(mcmeta, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("pack");
            JsonElement d = pack == null ? null : pack.get("description");
            if (d == null || d.isJsonNull()) {
                return "";
            }
            return d.isJsonPrimitive() ? d.getAsString() : d.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
