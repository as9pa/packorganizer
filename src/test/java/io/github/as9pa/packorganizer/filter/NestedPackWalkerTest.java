package io.github.as9pa.packorganizer.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NestedPackWalkerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File root() {
        return tmp.getRoot();
    }

    private File file(String rel) throws IOException {
        File f = new File(root(), rel);
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
        return f;
    }

    private File dirPack(String rel) throws IOException {
        file(rel + "/pack.mcmeta");
        return new File(root(), rel);
    }

    private List<String> found() {
        List<String> out = new ArrayList<String>();
        for (File f : NestedPackWalker.find(root())) {
            out.add(PackPaths.relative(root(), f));
        }
        return out;
    }

    @Test
    public void findsZipsAtDepthThreeAndSkipsTopLevel() throws IOException {
        file("top.zip");
        file("a/one.zip");
        file("a/b/two.zip");
        file("a/b/c/three.zip");
        assertEquals(Arrays.asList("a/b/c/three.zip", "a/b/two.zip", "a/one.zip"), found());
    }

    @Test
    public void findsDirectoryPacks() throws IOException {
        dirPack("pvp/Faithful");
        file("pvp/notes.txt");
        new File(root(), "pvp/empty").mkdirs();
        assertEquals(Arrays.asList("pvp/Faithful"), found());
    }

    @Test
    public void doesNotDescendIntoPacks() throws IOException {
        dirPack("pvp/Outer");
        file("pvp/Outer/inner.zip");
        dirPack("pvp/Outer/assets/Inner");
        dirPack("TopPack");
        file("TopPack/sub/hidden.zip");
        assertEquals(Arrays.asList("pvp/Outer"), found());
    }

    @Test
    public void upperCaseZipIsIgnored() throws IOException {
        file("pvp/Loud.ZIP");
        file("pvp/quiet.zip");
        assertEquals(Arrays.asList("pvp/quiet.zip"), found());
    }

    @Test
    public void childrenSortCaseInsensitive() throws IOException {
        file("f/b.zip");
        file("f/A.zip");
        file("f/c.zip");
        file("F2/x.zip");
        assertEquals(Arrays.asList("f/A.zip", "f/b.zip", "f/c.zip", "F2/x.zip"), found());
    }

    @Test
    public void windowsSeparatorsAreNormalized() throws IOException {
        File pack = file("a/b/p.zip");
        assertEquals("a/b/p.zip", PackPaths.relative(root(), pack));
        assertEquals("p.zip", PackPaths.shortName("a\\b\\p.zip"));
        assertEquals("a/b", PackPaths.folderOf("a\\b\\p.zip"));
        assertEquals("p.zip", PackPaths.shortName("a/b/p.zip"));
        assertEquals("a/b", PackPaths.folderOf("a/b/p.zip"));
        assertEquals("p.zip", PackPaths.shortName("p.zip"));
        assertEquals("", PackPaths.folderOf("p.zip"));
    }

    @Test
    public void folderWithNoPacksGivesEmpty() throws IOException {
        file("a/readme.txt");
        new File(root(), "b/c").mkdirs();
        assertTrue(found().isEmpty());
    }

    @Test
    public void missingRootGivesEmpty() {
        assertTrue(NestedPackWalker.find(new File(root(), "nope")).isEmpty());
    }
}
