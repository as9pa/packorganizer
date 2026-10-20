package io.github.as9pa.packorganizer.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns pack paths relative to resourcepacks (separated by "/") into the rows of the folder view.
 * Folders are synthetic: one exists only while a pack sits beneath it. Pure logic; must not touch Minecraft classes.
 */
public final class PackTree {
    private PackTree() {}

    /**
     * One pack as the list sees it; {@code visible} is false when the active filters hide it. A
     * {@code favorite} pack is listed first in its own folder, above that folder's subfolders.
     */
    public static final class Item {
        public final String relPath;
        public final boolean visible;
        public final double sortKey;
        public final boolean selected;
        public final boolean favorite;

        public Item(String relPath, boolean visible, double sortKey, boolean selected) {
            this(relPath, visible, sortKey, selected, false);
        }

        public Item(String relPath, boolean visible, double sortKey, boolean selected, boolean favorite) {
            this.relPath = relPath;
            this.visible = visible;
            this.sortKey = sortKey;
            this.selected = selected;
            this.favorite = favorite;
        }
    }

    public abstract static class Row {
        public final int depth;

        Row(int depth) {
            this.depth = depth;
        }
    }

    /** A folder row; {@code total} counts every pack beneath it (available plus selected, hidden or not). */
    public static final class Folder extends Row {
        public final String path;
        public final String name;
        public final int total;
        public final int selectedCount;
        public final boolean open;

        Folder(String path, String name, int depth, int total, int selectedCount, boolean open) {
            super(depth);
            this.path = path;
            this.name = name;
            this.total = total;
            this.selectedCount = selectedCount;
            this.open = open;
        }
    }

    /** A pack row; {@code index} points into the item list given to {@link #build}. */
    public static final class Pack extends Row {
        public final int index;

        Pack(int index, int depth) {
            super(depth);
            this.index = index;
        }
    }

    private static final class Node {
        final String path;
        final String name;
        final Map<String, Node> folders = new LinkedHashMap<String, Node>();
        final List<Integer> packs = new ArrayList<Integer>();
        int total;
        int selected;
        int visible;

        Node(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    private static final Comparator<Node> FOLDER_ORDER = new Comparator<Node>() {
        @Override
        public int compare(Node a, Node b) {
            int c = a.name.compareToIgnoreCase(b.name);
            return c != 0 ? c : a.name.compareTo(b.name);
        }
    };

    /**
     * Lays out the rows. A folder is open when {@code openFolders} holds its path. Folders with no
     * visible pack beneath are left out. With {@code sortByKey} the packs in each folder go by sortKey
     * descending, else in source order. Favorite packs come first in each folder, before its subfolders.
     */
    public static List<Row> build(List<Item> items, Set<String> openFolders, boolean sortByKey) {
        Node root = new Node("", "");
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            String[] parts = trimSlashes(item.relPath).split("/");
            Node node = root;
            count(node, item);
            for (int p = 0; p < parts.length - 1; p++) {
                if (parts[p].isEmpty()) {
                    continue;
                }
                Node child = node.folders.get(parts[p]);
                if (child == null) {
                    child = new Node(node.path.isEmpty() ? parts[p] : node.path + "/" + parts[p], parts[p]);
                    node.folders.put(parts[p], child);
                }
                node = child;
                count(node, item);
            }
            node.packs.add(i);
        }
        List<Row> rows = new ArrayList<Row>();
        emit(root, 0, items, openFolders, sortByKey, rows);
        return rows;
    }

    private static void count(Node node, Item item) {
        node.total++;
        if (item.selected) {
            node.selected++;
        }
        if (item.visible) {
            node.visible++;
        }
    }

    private static void emit(Node node, int depth, final List<Item> items, Set<String> openFolders, boolean sortByKey,
            List<Row> rows) {
        List<Integer> packs = new ArrayList<Integer>(node.packs);
        if (sortByKey) {
            // Stable, so equal keys keep source order.
            Collections.sort(packs, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Double.compare(items.get(b).sortKey, items.get(a).sortKey);
                }
            });
        }
        // Favorites lead the level, above its subfolders; the rest keep their place after the subfolders.
        for (Integer index : packs) {
            Item item = items.get(index);
            if (item.visible && item.favorite) {
                rows.add(new Pack(index, depth));
            }
        }
        List<Node> folders = new ArrayList<Node>(node.folders.values());
        Collections.sort(folders, FOLDER_ORDER);
        for (Node folder : folders) {
            if (folder.visible == 0) {
                continue;
            }
            boolean open = openFolders.contains(folder.path);
            rows.add(new Folder(folder.path, folder.name, depth, folder.total, folder.selected, open));
            if (open) {
                emit(folder, depth + 1, items, openFolders, sortByKey, rows);
            }
        }
        for (Integer index : packs) {
            Item item = items.get(index);
            if (item.visible && !item.favorite) {
                rows.add(new Pack(index, depth));
            }
        }
    }

    /**
     * The flat list's order: the indices {@code favorite} marks first, then the rest, each group keeping
     * its order in {@code order}. {@code favorite} is indexed by the values in {@code order}.
     */
    public static List<Integer> favoritesFirst(List<Integer> order, boolean[] favorite) {
        List<Integer> out = new ArrayList<Integer>(order.size());
        for (Integer i : order) {
            if (favorite[i]) {
                out.add(i);
            }
        }
        for (Integer i : order) {
            if (!favorite[i]) {
                out.add(i);
            }
        }
        return out;
    }

    private static String trimSlashes(String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(start, end);
    }
}
