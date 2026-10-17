package io.github.as9pa.packorganizer.filter;

import java.io.File;

/**
 * Names a nested pack by its path relative to the resourcepacks folder, always with "/" separators.
 * Must not touch Minecraft classes.
 */
public final class PackPaths {
    private PackPaths() {}

    /** The path of the pack relative to root, like "pvp/16x/Pack.zip". */
    public static String relative(File root, File pack) {
        String base = root.getAbsolutePath();
        String path = pack.getAbsolutePath();
        String rel = path.startsWith(base) ? path.substring(base.length()) : pack.getPath();
        rel = normalize(rel);
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        return rel;
    }

    /** The last segment of a relative path, like "Pack.zip". */
    public static String shortName(String rel) {
        String n = normalize(rel);
        return n.substring(n.lastIndexOf('/') + 1);
    }

    /** The folder part of a relative path, like "pvp/16x", or "" for a top-level pack. */
    public static String folderOf(String rel) {
        String n = normalize(rel);
        int slash = n.lastIndexOf('/');
        return slash < 0 ? "" : n.substring(0, slash);
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
