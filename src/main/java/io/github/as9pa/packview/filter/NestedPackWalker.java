package io.github.as9pa.packview.filter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds packs inside plain subfolders of resourcepacks, which vanilla does not list.
 * Uses vanilla's own pack test; top-level packs are left to vanilla. Must not touch Minecraft classes.
 */
public final class NestedPackWalker {
    private static final int MAX_DEPTH = 32;
    private static final Comparator<File> BY_NAME = new Comparator<File>() {
        @Override
        public int compare(File a, File b) {
            return a.getName().compareToIgnoreCase(b.getName());
        }
    };

    private NestedPackWalker() {}

    /** Packs at depth 1 or more under root, depth first, children sorted case-insensitively by name. */
    public static List<File> find(File root) {
        List<File> out = new ArrayList<File>();
        Set<String> visited = new HashSet<String>();
        if (root.isDirectory() && visit(root, visited)) {
            for (File child : sortedChildren(root)) {
                if (child.isDirectory() && !isPack(child)) {
                    walk(child, 1, visited, out);
                }
            }
        }
        return out;
    }

    /** Vanilla's test: a ".zip" file (case-sensitive) or a directory holding a pack.mcmeta file. */
    public static boolean isPack(File f) {
        return (f.isFile() && f.getName().endsWith(".zip"))
            || (f.isDirectory() && new File(f, "pack.mcmeta").isFile());
    }

    private static void walk(File dir, int depth, Set<String> visited, List<File> out) {
        if (depth > MAX_DEPTH || !visit(dir, visited)) {
            return;
        }
        for (File child : sortedChildren(dir)) {
            if (isPack(child)) {
                out.add(child);
            } else if (child.isDirectory()) {
                walk(child, depth + 1, visited, out);
            }
        }
    }

    /** Records the canonical path; false when it was already seen or cannot be resolved. */
    private static boolean visit(File dir, Set<String> visited) {
        try {
            return visited.add(dir.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static List<File> sortedChildren(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return new ArrayList<File>();
        }
        List<File> list = new ArrayList<File>(Arrays.asList(children));
        list.sort(BY_NAME);
        return list;
    }
}
