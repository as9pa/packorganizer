package io.github.as9pa.packorganizer.filter;

import java.util.Set;

/**
 * Which folders the user opened. Clicks edit the saved set; folders are only shown while no filter is
 * active, so the saved layout is what comes back once the filters clear. Pure logic; must not touch Minecraft classes.
 */
public final class FolderState {
    private final Set<String> openFolders;

    /** {@code openFolders} is the live saved set (Settings.openFolders); clicks edit it in place. */
    public FolderState(Set<String> openFolders) {
        this.openFolders = openFolders;
    }

    public Set<String> openFolders() {
        return openFolders;
    }

    /** Flips the folder that was shown as {@code wasOpen}. */
    public void toggle(String path, boolean wasOpen) {
        if (wasOpen) {
            openFolders.remove(path);
        } else {
            openFolders.add(path);
        }
    }
}
