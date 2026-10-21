package io.github.as9pa.packview.gui;

import io.github.as9pa.packview.filter.PackTree;
import java.util.function.Consumer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.util.ResourceLocation;

/**
 * A synthetic folder row in the available list. Draws itself fully (no super call), so the
 * ResourcePackListEntry mixin's indent and badge never run on it; it applies its own indent.
 */
public final class FolderRowEntry extends ResourcePackListEntry {
    private static final int NAME_COLOUR = 0xFFFFFF;
    private static final int COUNT_COLOUR = 0x808080;
    private static final int ARROW_COLOUR = 0xFFE0E0E0;
    private static final int ARROW_SHADOW = 0xFF383838;
    /** The arrow's box: 5 px along its long side (the font has no usable triangle glyphs). */
    private static final int ARROW_SIZE = 5;

    private static ResourceLocation iconTexture;

    private final String path;
    private String name;
    private int total;
    private int selectedCount;
    private boolean open;
    private Consumer<String> onToggle;

    public FolderRowEntry(GuiScreenResourcePacks screen, String path) {
        super(screen);
        this.path = path;
        int slash = path.lastIndexOf('/');
        this.name = slash < 0 ? path : path.substring(slash + 1);
    }

    public String path() {
        return path;
    }

    /** Copies name, counts, open state and depth from a freshly built tree row. */
    public void update(PackTree.Folder folder) {
        name = folder.name;
        total = folder.total;
        selectedCount = folder.selectedCount;
        open = folder.open;
        ((RowLayout) (Object) this).packview$setDepth(folder.depth);
    }

    /** Called with this folder's path when the row is clicked. */
    public void setOnToggle(Consumer<String> onToggle) {
        this.onToggle = onToggle;
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight,
                          int mouseX, int mouseY, boolean isSelected) {
        int depth = ((RowLayout) (Object) this).packview$depth();
        int indent = RowLayout.indentPx(depth);
        RowLayout.drawGuides(x, y, slotHeight, depth);
        int left = x + indent;

        mc.getTextureManager().bindTexture(icon());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawModalRectWithCustomSizedTexture(left, y, 0.0F, 0.0F, FolderIcon.SIZE, FolderIcon.SIZE,
                FolderIcon.SIZE, FolderIcon.SIZE);

        FontRenderer font = mc.fontRendererObj;
        int arrowX = x + RowBadge.RIGHT_EDGE - ARROW_SIZE;
        // Centred on the name line (font rows y+1..y+8).
        int arrowY = open ? y + 4 : y + 3;
        drawArrow(arrowX + 1, arrowY + 1, open, ARROW_SHADOW);
        drawArrow(arrowX, arrowY, open, ARROW_COLOUR);

        int textX = left + FolderIcon.SIZE + 2;
        int maxWidth = arrowX - 4 - textX;
        String shown = name;
        if (font.getStringWidth(shown) > maxWidth) {
            shown = font.trimStringToWidth(shown, maxWidth - font.getStringWidth("...")) + "...";
        }
        font.drawStringWithShadow(shown, textX, y + 1, NAME_COLOUR);
        font.drawStringWithShadow(countText(), textX, y + 12, COUNT_COLOUR);
    }

    /** A pixel triangle: pointing down when open (5 wide, 3 tall), right when closed (3 wide, 5 tall). */
    private static void drawArrow(int left, int top, boolean open, int colour) {
        if (open) {
            for (int r = 0; r < 3; r++) {
                Gui.drawRect(left + r, top + r, left + ARROW_SIZE - r, top + r + 1, colour);
            }
        } else {
            for (int r = 0; r < ARROW_SIZE; r++) {
                int width = Math.min(r, ARROW_SIZE - 1 - r) + 1;
                Gui.drawRect(left + 1, top + r, left + 1 + width, top + r + 1, colour);
            }
        }
    }

    private String countText() {
        String text = total + (total == 1 ? " pack" : " packs");
        return selectedCount > 0 ? text + ", " + selectedCount + " selected" : text;
    }

    @Override
    public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
        open = !open;
        if (onToggle != null) {
            onToggle.accept(path);
        }
        return true;
    }

    private ResourceLocation icon() {
        if (iconTexture == null) {
            DynamicTexture texture = new DynamicTexture(FolderIcon.SIZE, FolderIcon.SIZE);
            int[] pixels = FolderIcon.pixels();
            System.arraycopy(pixels, 0, texture.getTextureData(), 0, pixels.length);
            texture.updateDynamicTexture();
            iconTexture = mc.getTextureManager().getDynamicTextureLocation("packview_folder", texture);
        }
        return iconTexture;
    }

    // Abstract members of ResourcePackListEntry; drawEntry above never calls them.

    @Override
    protected int func_183019_a() {
        return 1; // compatible pack format: no red "incompatible" background
    }

    @Override
    protected String func_148311_a() {
        return "";
    }

    @Override
    protected String func_148312_b() {
        return name;
    }

    @Override
    protected void func_148313_c() {
        mc.getTextureManager().bindTexture(icon());
    }

    @Override
    protected boolean func_148310_d() {
        return false; // no select/move arrows
    }
}
