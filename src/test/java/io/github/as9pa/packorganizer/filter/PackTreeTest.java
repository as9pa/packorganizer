package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class PackTreeTest {
    private static PackTree.Item item(String path) {
        return new PackTree.Item(path, true, 0, false);
    }

    private static PackTree.Item item(String path, boolean visible, double sortKey, boolean selected) {
        return new PackTree.Item(path, visible, sortKey, selected);
    }

    private static Set<String> open(String... paths) {
        return new HashSet<String>(Arrays.asList(paths));
    }

    private static List<PackTree.Row> build(List<PackTree.Item> items, Set<String> openFolders) {
        return PackTree.build(items, openFolders, false);
    }

    /** Folders as "F:path:depth:total:selected:open", packs as "P:index:depth". */
    private static List<String> describe(List<PackTree.Row> rows) {
        List<String> out = new ArrayList<String>();
        for (PackTree.Row row : rows) {
            if (row instanceof PackTree.Folder) {
                PackTree.Folder f = (PackTree.Folder) row;
                out.add("F:" + f.path + ":" + f.depth + ":" + f.total + ":" + f.selectedCount + ":" + (f.open ? "o" : "c"));
            } else {
                PackTree.Pack p = (PackTree.Pack) row;
                out.add("P:" + p.index + ":" + p.depth);
            }
        }
        return out;
    }

    @Test
    public void flatListKeepsSourceOrder() {
        List<PackTree.Item> items = Arrays.asList(item("b.zip"), item("a.zip"), item("c"));
        assertEquals(Arrays.asList("P:0:0", "P:1:0", "P:2:0"), describe(build(items, open())));
    }

    @Test
    public void foldersComeFirstSortedCaseInsensitive() {
        List<PackTree.Item> items = Arrays.asList(
                item("top.zip"),
                item("zeta/z.zip"),
                item("Beta/b.zip"),
                item("alpha/a.zip"));
        assertEquals(Arrays.asList(
                "F:alpha:0:1:0:c",
                "F:Beta:0:1:0:c",
                "F:zeta:0:1:0:c",
                "P:0:0"), describe(build(items, open())));
    }

    @Test
    public void folderNameIsLastSegment() {
        List<PackTree.Item> items = Arrays.asList(item("PvP/16x/a.zip"));
        List<PackTree.Row> rows = build(items, open("PvP"));
        assertEquals("PvP", ((PackTree.Folder) rows.get(0)).name);
        assertEquals("16x", ((PackTree.Folder) rows.get(1)).name);
        assertEquals("PvP/16x", ((PackTree.Folder) rows.get(1)).path);
    }

    @Test
    public void openFoldersShowChildrenWithDepth() {
        List<PackTree.Item> items = Arrays.asList(
                item("a/x.zip"),
                item("a/b/y.zip"),
                item("a/w.zip"));
        assertEquals(Arrays.asList(
                "F:a:0:3:0:o",
                "F:a/b:1:1:0:o",
                "P:1:2",
                "P:0:1",
                "P:2:1"), describe(build(items, open("a", "a/b"))));
    }

    @Test
    public void closedParentHidesOpenChild() {
        List<PackTree.Item> items = Arrays.asList(item("a/b/y.zip"));
        assertEquals(Arrays.asList("F:a:0:1:0:c"), describe(build(items, open("a/b"))));
    }

    @Test
    public void countsAreRecursiveAndIncludeSelectedAndHidden() {
        List<PackTree.Item> items = Arrays.asList(
                item("a/x.zip", true, 0, false),
                item("a/b/y.zip", false, 0, true),
                item("a/b/z.zip", true, 0, true),
                item("a/b/c/q.zip", true, 0, false));
        List<String> rows = describe(build(items, open("a", "a/b", "a/b/c")));
        assertEquals(Arrays.asList(
                "F:a:0:4:2:o",
                "F:a/b:1:3:2:o",
                "F:a/b/c:2:1:0:o",
                "P:3:3",
                "P:2:2",
                "P:0:1"), rows);
    }

    @Test
    public void foldersWithoutPacksDoNotExist() {
        List<PackTree.Item> items = Arrays.asList(item("a/b/c/deep.zip"));
        List<String> rows = describe(build(items, open("a", "a/b", "a/b/c", "empty")));
        assertEquals(Arrays.asList("F:a:0:1:0:o", "F:a/b:1:1:0:o", "F:a/b/c:2:1:0:o", "P:0:3"), rows);
    }

    @Test
    public void foldersWithoutVisibleDescendantAreHidden() {
        List<PackTree.Item> items = Arrays.asList(
                item("a/x.zip", false, 0, false),
                item("a/b/y.zip", false, 0, false),
                item("c/z.zip", true, 0, false),
                item("c/d/w.zip", false, 0, false),
                item("top.zip", false, 0, false));
        assertEquals(Arrays.asList("F:c:0:2:0:o", "P:2:1"), describe(build(items, open("a", "a/b", "c", "c/d"))));
    }

    @Test
    public void colorSortOrdersPacksByKeyDescendingInsideEachFolder() {
        List<PackTree.Item> items = Arrays.asList(
                item("a/p.zip", true, 0.2, false),
                item("a/q.zip", true, 0.9, false),
                item("r.zip", true, 0.1, false),
                item("s.zip", true, 0.5, false),
                item("t.zip", true, 0.5, false));
        List<PackTree.Row> rows = PackTree.build(items, open("a"), true);
        assertEquals(Arrays.asList("F:a:0:2:0:o", "P:1:1", "P:0:1", "P:3:0", "P:4:0", "P:2:0"), describe(rows));
    }

    @Test
    public void trailingSlashOnFolderPackIsIgnored() {
        List<PackTree.Item> items = Arrays.asList(item("a/dirpack/"), item("b/"));
        assertEquals(Arrays.asList("F:a:0:1:0:o", "P:0:1", "P:1:0"), describe(build(items, open("a"))));
    }

    @Test
    public void emptyInputGivesNoRows() {
        assertTrue(build(new ArrayList<PackTree.Item>(), open()).isEmpty());
        assertFalse(build(Arrays.asList(item("x.zip")), open()).isEmpty());
    }

    private static PackTree.Item fav(String path) {
        return new PackTree.Item(path, true, 0, false, true);
    }

    @Test
    public void favoriteComesAboveSubfolders() {
        List<PackTree.Item> items = Arrays.asList(item("a/x.zip"), item("b.zip"), fav("c.zip"), item("d.zip"));
        assertEquals(Arrays.asList("P:2:0", "F:a:0:1:0:c", "P:1:0", "P:3:0"), describe(build(items, open())));
    }

    @Test
    public void favoritesKeepTheirRelativeOrder() {
        List<PackTree.Item> items = Arrays.asList(fav("z.zip"), item("m.zip"), fav("a.zip"), item("f/x.zip"));
        assertEquals(Arrays.asList("P:0:0", "P:2:0", "F:f:0:1:0:c", "P:1:0"), describe(build(items, open())));
    }

    @Test
    public void nestedFavoriteStaysInItsOwnFolder() {
        List<PackTree.Item> items = Arrays.asList(item("a/one.zip"), item("a/b/deep.zip"), fav("a/two.zip"), item("top.zip"));
        // Leads folder a, above a/b, but never rises to the top level.
        assertEquals(Arrays.asList("F:a:0:3:0:o", "P:2:1", "F:a/b:1:1:0:c", "P:0:1", "P:3:0"),
                describe(build(items, open("a"))));
        // A closed folder hides its favorite like any other pack.
        assertEquals(Arrays.asList("F:a:0:3:0:c", "P:3:0"), describe(build(items, open())));
    }

    @Test
    public void hiddenFavoriteIsNotShown() {
        List<PackTree.Item> items = Arrays.asList(new PackTree.Item("x.zip", false, 0, false, true), item("y.zip"));
        assertEquals(Arrays.asList("P:1:0"), describe(build(items, open())));
    }

    @Test
    public void noFavoritesLeavesOrderUnchanged() {
        List<PackTree.Item> plain = Arrays.asList(item("b.zip"), item("a/x.zip"), item("c.zip"), item("a/b/y.zip"));
        List<PackTree.Item> flagged = Arrays.asList(new PackTree.Item("b.zip", true, 0, false, false),
                new PackTree.Item("a/x.zip", true, 0, false, false), new PackTree.Item("c.zip", true, 0, false, false),
                new PackTree.Item("a/b/y.zip", true, 0, false, false));
        assertEquals(Arrays.asList("F:a:0:2:0:o", "F:a/b:1:1:0:o", "P:3:2", "P:1:1", "P:0:0", "P:2:0"),
                describe(build(plain, open("a", "a/b"))));
        assertEquals(describe(build(plain, open("a", "a/b"))), describe(build(flagged, open("a", "a/b"))));
    }

    @Test
    public void favoritesFirstInTheFlatList() {
        boolean[] favorite = {false, true, false, true, false};
        assertEquals(Arrays.asList(3, 1, 4, 0, 2), PackTree.favoritesFirst(Arrays.asList(4, 3, 0, 1, 2), favorite));
        assertEquals(Arrays.asList(0, 2, 4), PackTree.favoritesFirst(Arrays.asList(0, 2, 4), favorite));
        assertTrue(PackTree.favoritesFirst(new ArrayList<Integer>(), favorite).isEmpty());
    }
}
