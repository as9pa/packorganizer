package io.github.as9pa.packorganizer.gui;

import java.util.List;
import net.minecraft.client.resources.ResourcePackListEntry;

/** Implemented by {@code GuiResourcePackList} via mixin: when a view is set, the widget reads rows from it. */
public interface FilteredList {
    void packorganizer$setView(List<ResourcePackListEntry> view);
}
