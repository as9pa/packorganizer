package io.github.as9pa.packorganizer.gui;

import io.github.as9pa.packorganizer.EntryExtension;
import io.github.as9pa.packorganizer.PackOrganizer;
import io.github.as9pa.packorganizer.Settings;
import io.github.as9pa.packorganizer.filter.ColorFeatures;
import io.github.as9pa.packorganizer.filter.ColorScore;
import io.github.as9pa.packorganizer.filter.FolderState;
import io.github.as9pa.packorganizer.filter.PackColors;
import io.github.as9pa.packorganizer.filter.PackFilter;
import io.github.as9pa.packorganizer.filter.PackPaths;
import io.github.as9pa.packorganizer.filter.PackResolution;
import io.github.as9pa.packorganizer.filter.PackTree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;

/**
 * The row above the two lists: search box, resolution chips and the Previews toggle, with the match
 * counter on the screen title's line above its right end. Owns the filtered view
 * that the Available list widget reads through. With packFolders on, that view is the folder tree:
 * folder rows (cached per path) and indented packs. With colorFilter on it also owns the color wheel:
 * docked left of the lists when there is room, else opened as a popup from a "Color" chip at the
 * toolbar's left edge. A pick scores, filters and sorts the view. One instance per initGui.
 */
public final class PacksToolbar {
    public static final int TOOLBAR_Y = 28;
    public static final int LIST_TOP = 52;
    public static final int WIDTH = 408;
    public static final int HEIGHT = 20;
    public static final int SEARCH_WIDTH = 140;
    public static final int CHIP_GAP = 2;
    public static final int SEARCH_ID = 199;
    public static final int CHIP_ID_BASE = 100;
    public static final int COLOR_ID = 198;
    public static final int PREVIEWS_ID = 197;
    /** The counter's y above the toolbar: on the line vanilla draws the screen title on (y 16), since the toggle fills the row. */
    private static final int COUNTER_ABOVE = 12;
    private static final String PREVIEWS_LABEL = "Previews";
    /** The wheel docks left of the lists when they start at least this far from the screen edge. */
    private static final int DOCK_MIN_LEFT = 136;
    private static final int PANEL_GAP = 8;
    private static final String COLOR_LABEL = "Color";
    private static final int MAX_QUERY_LENGTH = 40;
    private static final String PLACEHOLDER = "Search packs";
    private static final int PLACEHOLDER_COLOUR = 0x707070;
    private static final int COUNTER_COLOUR = 0xA0A0A0;

    private final GuiScreenResourcePacks screen;
    private final FontRenderer font;
    private final int left;
    private final int top;
    private final List<ResourcePackListEntry> source;
    private final List<ResourcePackListEntry> selected;
    private final List<ResourcePackListEntry> visible = new ArrayList<>();
    private final List<ResourcePackListEntry> snapshot = new ArrayList<>();
    private final List<ResourcePackListEntry> selectedSnapshot = new ArrayList<>();
    private final List<PackResolution> snapshotResolutions = new ArrayList<>();
    private final List<PackColors> snapshotColors = new ArrayList<>();
    private final List<ChipButton> chips = new ArrayList<>();
    /** On/off for listPreviews; looks like a chip but is not one of the mutually exclusive filters. */
    private final ChipButton previewsToggle;
    /** The chips, then the toggle: everything the screen adds to its buttonList. */
    private final List<ChipButton> buttons = new ArrayList<>();
    private final Map<String, FolderRowEntry> folderRows = new HashMap<>();
    /** Open state of each folder row as last shown; the row flips its own arrow before telling us. */
    private final Map<String, Boolean> shownOpen = new HashMap<>();
    private final FolderState folders = new FolderState(PackOrganizer.settings().openFolders);
    private final GuiTextField search;
    private final int searchLeft;
    /** Null when colorFilter is off. */
    private final ColorWheelPanel panel;
    /** The popup's opener; null when the panel is docked or colorFilter is off. */
    private final GuiButton colorChip;
    /** Starts closed on every initGui (one toolbar per initGui, so also on resize or a layout switch). */
    private boolean popupOpen;
    /** Whether the last refresh applied a color pick, and the default-pack colors it used. */
    private boolean colorApplied;
    private PackColors appliedDefaults;
    private String query;
    private int matchCount;
    private boolean dirty = true;

    /** {@code selected} is read only for the folder rows' counts; its rows are never shown here. */
    public PacksToolbar(GuiScreenResourcePacks screen, FontRenderer font, int left, int top,
                        List<ResourcePackListEntry> source, List<ResourcePackListEntry> selected, String initialQuery) {
        this.screen = screen;
        this.font = font;
        this.left = left;
        this.top = top;
        this.source = source;
        this.selected = selected;
        int searchX = left;
        if (!PackOrganizer.settings().colorFilter) {
            panel = null;
            colorChip = null;
        } else if (left >= DOCK_MIN_LEFT) {
            panel = new ColorWheelPanel(left - ColorWheelPanel.WIDTH - PANEL_GAP, top);
            colorChip = null;
        } else {
            panel = new ColorWheelPanel(left, top + HEIGHT + 2);
            panel.setPopup(true);
            int chipWidth = font.getStringWidth(COLOR_LABEL) + 10;
            colorChip = new GuiButton(COLOR_ID, left, top, chipWidth, HEIGHT, COLOR_LABEL);
            searchX = left + chipWidth + CHIP_GAP;   // the search box gives up the chip's width
        }
        if (panel != null) {
            panel.setOnChange(() -> dirty = true);
        }
        searchLeft = searchX;
        // GuiTextField draws its 1 px border outside the box, so inset by 1 to occupy exactly its slot.
        search = new GuiTextField(SEARCH_ID, font, searchLeft + 1, top + 1, SEARCH_WIDTH - (searchLeft - left) - 2, HEIGHT - 2);
        search.setMaxStringLength(MAX_QUERY_LENGTH);
        search.setText(initialQuery == null ? "" : initialQuery);
        query = PackFilter.normalizeQuery(search.getText());

        int x = left + SEARCH_WIDTH + 4;
        int id = CHIP_ID_BASE;
        PackFilter.Chip active = PackFilter.activeChip();
        for (PackFilter.Chip chip : PackFilter.Chip.values()) {
            int width = font.getStringWidth(chip.label) + 10;
            ChipButton button = new ChipButton(id++, x, top, width, chip);
            button.setActive(chip == active);
            chips.add(button);
            x += width + CHIP_GAP;
        }
        // Right of Overlay; with the vanilla font it ends 3 px inside WIDTH.
        previewsToggle = new ChipButton(PREVIEWS_ID, x, top, font.getStringWidth(PREVIEWS_LABEL) + 10, PREVIEWS_LABEL);
        previewsToggle.setActive(PackOrganizer.settings().listPreviews);
        buttons.addAll(chips);
        buttons.add(previewsToggle);
    }

    /** Add these (the chips, then the Previews toggle) to the screen's buttonList; the screen draws and clicks them. */
    public List<ChipButton> chips() {
        return buttons;
    }

    /** The "Color" chip to add to the screen's buttonList, or null when there is none. */
    public GuiButton colorChip() {
        return colorChip;
    }

    /** The live filtered view. Same instance for the toolbar's lifetime; contents change on refresh(). */
    public List<ResourcePackListEntry> visible() {
        return visible;
    }

    public String query() {
        return search.getText();
    }

    /** Recomputes the view when the filter or the source list changed. Cheap when neither did. */
    public void refresh() {
        boolean colorOn = panel != null && ColorWheelPanel.hasPick();
        PackColors defaults = colorOn ? DefaultColors.get() : null;
        if (!dirty && colorOn == colorApplied && defaults == appliedDefaults && sameAsSnapshot()) {
            return;
        }
        dirty = false;
        colorApplied = colorOn;
        appliedDefaults = defaults;
        snapshot.clear();
        snapshotResolutions.clear();
        snapshotColors.clear();
        selectedSnapshot.clear();
        selectedSnapshot.addAll(selected);
        visible.clear();
        matchCount = 0;
        PackFilter.Chip chip = PackFilter.activeChip();
        boolean[] matched = new boolean[source.size()];
        for (int i = 0; i < source.size(); i++) {
            ResourcePackListEntry entry = source.get(i);
            // Record the resolution and colors before matching: a scan landing in between then leaves the
            // snapshot looking stale, which costs one extra refresh instead of a view that never updates.
            snapshot.add(entry);
            snapshotResolutions.add(resolutionOf(entry));
            snapshotColors.add(colorsOf(entry));
            matched[i] = matches(chip, entry);
        }
        double[] scores = colorOn ? applyColor(matched, defaults) : null;
        for (boolean m : matched) {
            if (m) {
                matchCount++;
            }
        }
        // Any active filter shows the matches as one flat list, packs inside folders included; the folder
        // tree (with its saved open state) comes back once every filter is cleared.
        boolean filtering = !query.isEmpty() || chip != PackFilter.Chip.ALL || scores != null;
        if (!PackOrganizer.settings().packFolders || filtering) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < source.size(); i++) {
                setDepth(source.get(i), 0);
                if (matched[i]) {
                    order.add(i);
                }
            }
            if (scores != null) {
                // Stable, so equal scores keep source order.
                Collections.sort(order, (a, b) -> Double.compare(scores[b], scores[a]));
            }
            for (int i : order) {
                visible.add(source.get(i));
            }
            return;
        }
        buildTree(matched);
    }

    /**
     * Scores every available pack against the pick and notes how many of the packs the search and chip
     * let through reach the close threshold. Hides nothing: the caller sorts by score instead.
     * Packs whose colors are not scanned yet score 0. Returns the scores, indexed like source.
     */
    private double[] applyColor(boolean[] matched, PackColors defaults) {
        double hue = ColorWheelPanel.hue();
        double l = ColorWheelPanel.lightness();
        ColorFeatures defaultSword = defaults == null ? null : defaults.sword;
        ColorFeatures defaultArmor = defaults == null ? null : defaults.armor;
        double threshold = ColorScore.threshold(l);
        double[] scores = new double[source.size()];
        int close = 0;
        for (int i = 0; i < scores.length; i++) {
            PackColors colors = snapshotColors.get(i);
            scores[i] = colors == null ? 0 : ColorScore.packScore(colors.sword, colors.armor, defaultSword, defaultArmor, hue, l);
            if (matched[i] && scores[i] >= threshold) {
                close++;
            }
        }
        if (close == 0) {
            panel.setNote("No close packs");
        } else {
            panel.setNote(close == 1 ? "1 close pack" : close + " close packs");
        }
        return scores;
    }

    /** Lays the packs out under their folders, used only while no filter is active. Selected packs count toward folder totals only. */
    private void buildTree(boolean[] matched) {
        List<PackTree.Item> items = new ArrayList<>(source.size() + selected.size());
        for (int i = 0; i < source.size(); i++) {
            items.add(new PackTree.Item(relPathOf(source.get(i)), matched[i], 0, false));
        }
        for (ResourcePackListEntry entry : selected) {
            items.add(new PackTree.Item(relPathOf(entry), false, 0, true));
        }
        shownOpen.clear();
        for (PackTree.Row row : PackTree.build(items, folders.openFolders(), false)) {
            if (row instanceof PackTree.Folder) {
                PackTree.Folder folder = (PackTree.Folder) row;
                FolderRowEntry entry = folderRow(folder.path);
                entry.update(folder);
                shownOpen.put(folder.path, folder.open);
                visible.add(entry);
            } else {
                // Only available items are ever visible, so the index is always inside source.
                ResourcePackListEntry entry = source.get(((PackTree.Pack) row).index);
                setDepth(entry, row.depth);
                visible.add(entry);
            }
        }
    }

    private FolderRowEntry folderRow(String path) {
        FolderRowEntry entry = folderRows.get(path);
        if (entry == null) {
            entry = new FolderRowEntry(screen, path);
            entry.setOnToggle(this::toggleFolder);
            folderRows.put(path, entry);
        }
        return entry;
    }

    private void toggleFolder(String path) {
        folders.toggle(path, Boolean.TRUE.equals(shownOpen.get(path)));
        dirty = true;
    }

    private static void setDepth(ResourcePackListEntry entry, int depth) {
        // Defensive: if the entry mixin did not apply, rows simply stay flush left.
        if ((Object) entry instanceof RowLayout) {
            ((RowLayout) (Object) entry).packorganizer$setDepth(depth);
        }
    }

    /** The pack's path relative to resourcepacks; non-pack rows (the default pack) sit at the top level. */
    private static String relPathOf(ResourcePackListEntry entry) {
        if (!(entry instanceof ResourcePackListEntryFound)) {
            return "";
        }
        return ((ResourcePackListEntryFound) entry).func_148318_i().getResourcePackName();
    }

    private boolean sameAsSnapshot() {
        if (snapshot.size() != source.size() || selectedSnapshot.size() != selected.size()) {
            return false;
        }
        for (int i = 0; i < selectedSnapshot.size(); i++) {
            if (selectedSnapshot.get(i) != selected.get(i)) {
                return false;
            }
        }
        for (int i = 0; i < snapshot.size(); i++) {
            ResourcePackListEntry entry = source.get(i);
            if (snapshot.get(i) != entry) {
                return false;
            }
            // Entries are reused across scans; a finished scan only swaps the resolution reference.
            if (snapshotResolutions.get(i) != resolutionOf(entry)) {
                return false;
            }
            // Same for colors, which matter only while a pick is applied.
            if (colorApplied && snapshotColors.get(i) != colorsOf(entry)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(PackFilter.Chip chip, ResourcePackListEntry entry) {
        if (!(entry instanceof ResourcePackListEntryFound)) {
            return chip == PackFilter.Chip.ALL && query.isEmpty();
        }
        ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) entry).func_148318_i();
        return PackFilter.matches(chip, query, PackPaths.shortName(pack.getResourcePackName()), pack.getTexturePackDescription(), resolutionOf(entry));
    }

    /** The row's resolution right now, or null while the scan is pending or the row has none. */
    private static PackResolution resolutionOf(ResourcePackListEntry entry) {
        if (!(entry instanceof ResourcePackListEntryFound)) {
            return null;
        }
        ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) entry).func_148318_i();
        // Defensive: if the Entry mixin did not apply, nothing implements EntryExtension: treat it as still pending.
        return pack instanceof EntryExtension ? ((EntryExtension) pack).packorganizer$resolution() : null;
    }

    /** The row's scanned sword/armor colors, or null while the scan is pending or the row is not a pack. */
    private static PackColors colorsOf(ResourcePackListEntry entry) {
        if (!(entry instanceof ResourcePackListEntryFound)) {
            return null;
        }
        ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) entry).func_148318_i();
        return pack instanceof EntryExtension ? ((EntryExtension) pack).packorganizer$colors() : null;
    }

    /** True (and the filter updated, the popup toggled or Previews flipped and saved) when the button is one of ours. */
    public boolean activate(GuiButton button) {
        if (colorChip != null && button == colorChip) {
            popupOpen = !popupOpen;
            return true;
        }
        if (button == previewsToggle) {
            // Not a filter: the view, the active chip, the search and the folders all stay as they are.
            Settings settings = PackOrganizer.settings();
            settings.listPreviews = !settings.listPreviews;
            previewsToggle.setActive(settings.listPreviews);
            Settings.saveNow();
            return true;
        }
        if (!(button instanceof ChipButton)) {
            return false;
        }
        PackFilter.Chip chip = ((ChipButton) button).chip;
        PackFilter.setActiveChip(chip);
        for (ChipButton b : chips) {
            b.setActive(b.chip == chip);
        }
        dirty = true;
        return true;
    }

    /** True when the search box consumed the key. */
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!search.isFocused()) {
            return false;
        }
        search.textboxKeyTyped(typedChar, keyCode);
        String normalized = PackFilter.normalizeQuery(search.getText());
        if (!normalized.equals(query)) {
            query = normalized;
            dirty = true;
        }
        return true;
    }

    /** True when the color panel consumed the click; the screen must then not handle it. */
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (panelShown()) {
            if (panel.mouseClicked(mouseX, mouseY, mouseButton)) {
                return true;
            }
            // A click elsewhere closes the popup and goes through; the chip itself toggles it in activate.
            if (popupOpen && !overColorChip(mouseX, mouseY)) {
                popupOpen = false;
            }
        }
        search.mouseClicked(mouseX, mouseY, mouseButton);
        return false;
    }

    /** Closes the popup; true when it was open, so the key (Esc) that asked should go no further. */
    public boolean closePopup() {
        if (!popupOpen) {
            return false;
        }
        popupOpen = false;
        return true;
    }

    /** Continues a drag that began on the wheel. */
    public void mouseDragged(int mouseX, int mouseY) {
        if (panelShown()) {
            panel.mouseDragged(mouseX, mouseY);
        }
    }

    /** True while the popup is open under the mouse, so the lists below should not show hover. */
    public boolean popupUnder(int mouseX, int mouseY) {
        return popupOpen && panel.isMouseOver(mouseX, mouseY);
    }

    private boolean panelShown() {
        return panel != null && (colorChip == null || popupOpen);
    }

    private boolean overColorChip(int mouseX, int mouseY) {
        return mouseX >= colorChip.xPosition && mouseY >= colorChip.yPosition
                && mouseX < colorChip.xPosition + colorChip.width && mouseY < colorChip.yPosition + colorChip.height;
    }

    public void tick() {
        search.updateCursorCounter();
    }

    /** Call after the vanilla screen has drawn (buttons included). */
    public void draw(int mouseX, int mouseY) {
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) {
            font.drawStringWithShadow(PLACEHOLDER, searchLeft + 5, top + 6, PLACEHOLDER_COLOUR);
        }
        String counter = matchCount + " of " + source.size();
        font.drawStringWithShadow(counter, left + WIDTH - font.getStringWidth(counter), top - COUNTER_ABOVE, COUNTER_COLOUR);
        if (panelShown()) {
            panel.draw(mouseX, mouseY);
        }
    }
}
