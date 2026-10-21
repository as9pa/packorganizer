package io.github.as9pa.packview.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

public class FolderStateTest {
    @Test
    public void clickEditsSavedSet() {
        Set<String> saved = new LinkedHashSet<String>();
        FolderState state = new FolderState(saved);
        state.toggle("pvp", false);
        assertTrue(saved.contains("pvp"));
        state.toggle("pvp", true);
        assertFalse(saved.contains("pvp"));
    }

    @Test
    public void savedSetDrivesTheTree() {
        Set<String> saved = new LinkedHashSet<String>(Arrays.asList("a"));
        FolderState state = new FolderState(saved);
        state.toggle("a", true);
        java.util.List<PackTree.Row> rows = PackTree.build(
                Arrays.asList(new PackTree.Item("a/x.zip", true, 0, false)), state.openFolders(), false);
        assertEquals(1, rows.size());
        assertFalse(((PackTree.Folder) rows.get(0)).open);
    }
}
