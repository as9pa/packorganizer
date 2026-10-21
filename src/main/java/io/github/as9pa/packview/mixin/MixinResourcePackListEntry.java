package io.github.as9pa.packview.mixin;

import io.github.as9pa.packview.EntryExtension;
import io.github.as9pa.packview.PackView;
import io.github.as9pa.packview.filter.PreviewSpec;
import io.github.as9pa.packview.gui.FavoriteStar;
import io.github.as9pa.packview.gui.PacksToolbar;
import io.github.as9pa.packview.gui.PlayerPreviewPanel;
import io.github.as9pa.packview.gui.PreviewCache;
import io.github.as9pa.packview.gui.RowBadge;
import io.github.as9pa.packview.gui.RowLayout;
import io.github.as9pa.packview.gui.RowLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryDefault;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Indents rows by folder depth, draws the resolution badge on every row, and shortens the name
 * trim so the badge and indent never overlap the text. While a Found row is hovered and its preview is
 * ready, the name and description give way to a 2x8 grid of the pack's textures. In Previews mode
 * (listPreviews) every on-screen Found row shows its grid instead, and the hovered row its text; a grid row keeps
 * its badge only where the badge clears the grid. See {@link RowLook}. Favorites get a gold star left of the badge
 * (under it on a grid row), and the hovered pack row an outline star; clicking either toggles the favorite. See
 * {@link FavoriteStar}.
 */
@Mixin(ResourcePackListEntry.class)
public abstract class MixinResourcePackListEntry implements RowLayout {
    @Shadow @Final protected Minecraft mc;
    @Shadow @Final protected GuiScreenResourcePacks resourcePacksGUI;

    /** The row name as drawn, formatting codes included. */
    @Shadow protected abstract String func_148312_b();

    /** Preview grid start, right of the 32 px icon, from the shifted x. */
    @Unique private static final int PREVIEW_OFFSET = 34;
    @Unique private static final int PREVIEW_CELL = 16;
    @Unique private static final int PREVIEW_CELL_SMALL = 12;

    @Unique private int packview$rowDepth;
    /** Set at drawEntry HEAD, cleared at RETURN: the preview to draw instead of the text, or null. */
    @Unique private PreviewCache.Preview packview$preview;
    /** Set at drawEntry HEAD with the preview: true when the row's look drops the badge. */
    @Unique private boolean packview$hideBadge;
    /** Set at drawEntry HEAD: the row's favorite key (null for no pack) and whether a star shows this frame. */
    @Unique private String packview$starKey;
    @Unique private boolean packview$starFavorite;
    @Unique private boolean packview$starShown;
    /** Where the star was last drawn, relative to the unindented row, for the click test; starDrawn false if not. */
    @Unique private boolean packview$starDrawn;
    @Unique private int packview$starLeft;
    @Unique private int packview$starTop;

    @Override
    public void packview$setDepth(int depth) {
        packview$rowDepth = depth;
    }

    @Override
    public int packview$depth() {
        return packview$rowDepth;
    }

    /** Depth only indents the available list; the selected list stays flat. */
    @Unique
    private int packview$indentPx() {
        if (packview$rowDepth <= 0 || resourcePacksGUI.hasResourcePackEntry((ResourcePackListEntry) (Object) this)) {
            return 0;
        }
        return RowLayout.indentPx(packview$rowDepth);
    }

    /** Shifts the whole row right by the indent; the guides are drawn at RETURN. */
    @ModifyVariable(method = "drawEntry", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int packview$indentX(int x) {
        return x + packview$indentPx();
    }

    /**
     * drawEntry uses the literal 157 three times: twice for the name ("if (width > 157)" and
     * "trimStringToWidth(s, 157 - ...)"), once for the description. The name makes room for the
     * badge (when shown), the star (when shown) and the indent; the description makes room for the indent only.
     */
    @ModifyConstant(method = "drawEntry",
            constant = {@Constant(intValue = 157, ordinal = 0), @Constant(intValue = 157, ordinal = 1)},
            require = 2)
    private int packview$nameWidth(int vanillaWidth) {
        int width = PackView.settings().resolutionLabels ? RowBadge.NAME_WIDTH : vanillaWidth;
        if (packview$starShown) {
            width -= FavoriteStar.NAME_ROOM;
        }
        return width - packview$indentPx();
    }

    @ModifyConstant(method = "drawEntry", constant = @Constant(intValue = 157, ordinal = 2))
    private int packview$descriptionWidth(int vanillaWidth) {
        return vanillaWidth - packview$indentPx();
    }

    /**
     * isSelected is the hover flag. A hovered pack row is offered to the player preview. The first hover (or, in
     * Previews mode, the first frame on screen) starts the background load; RowLook picks text or grid once it is
     * ready. Off-screen rows request nothing, since vanilla draws every row of the list each frame.
     */
    @Inject(method = "drawEntry", at = @At("HEAD"))
    private void packview$lookUpPreview(int slotIndex, int x, int y, int listWidth, int slotHeight,
                                             int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        packview$preview = null;
        packview$hideBadge = false;
        packview$starKey = PacksToolbar.favoriteKey((ResourcePackListEntry) (Object) this);
        packview$starFavorite = PackView.settings().isFavorite(packview$starKey);
        packview$starShown = packview$starKey != null && (packview$starFavorite || isSelected);
        if (isSelected) {
            packview$offerHover();
        }
        boolean listPreviews = PackView.settings().listPreviews;
        boolean wanted = isSelected || listPreviews && RowLook.onScreen(y, slotHeight,
                PacksToolbar.LIST_TOP, resourcePacksGUI.height - PacksToolbar.LIST_BOTTOM_INSET);
        if (!wanted || !PackView.settings().packPreviews
                || !((Object) this instanceof ResourcePackListEntryFound)) {
            return;
        }
        ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) (Object) this).func_148318_i();
        // Defensive: if the Entry mixin did not apply, there is no pack file to read.
        if (!(pack instanceof EntryExtension) || ((EntryExtension) pack).packview$file() == null) {
            return;
        }
        PreviewCache.Preview preview = PreviewCache.get(((EntryExtension) pack).packview$file());
        RowLook look = RowLook.of(listPreviews, isSelected, preview != null);
        packview$preview = look.strip ? preview : null;
        packview$hideBadge = !look.label;
    }

    /** Found rows offer their pack file, the Default row offers no file (the vanilla look); folder rows nothing. */
    @Unique
    private void packview$offerHover() {
        if ((Object) this instanceof ResourcePackListEntryDefault) {
            PlayerPreviewPanel.offerHover(null, func_148312_b());
        } else if ((Object) this instanceof ResourcePackListEntryFound) {
            ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) (Object) this).func_148318_i();
            if (pack instanceof EntryExtension && ((EntryExtension) pack).packview$file() != null) {
                PlayerPreviewPanel.offerHover(((EntryExtension) pack).packview$file(), func_148312_b());
            }
        }
    }

    /** Both drawStringWithShadow calls (name and description lines) are skipped while the preview shows. */
    @Redirect(method = "drawEntry",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"),
            require = 1)
    private int packview$hideTextForPreview(FontRenderer font, String text, float x, float y, int color) {
        return packview$preview != null ? 0 : font.drawStringWithShadow(text, x, y, color);
    }

    /**
     * x here is the shifted x, so step back by the indent to draw the guides from the original x
     * and keep the badge and star right-aligned.
     */
    @Inject(method = "drawEntry", at = @At("RETURN"))
    private void packview$drawGuidesAndBadge(int slotIndex, int x, int y, int listWidth, int slotHeight,
                                     int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        int indent = packview$indentPx();
        int originalX = x - indent;
        if (indent > 0) {
            RowLayout.drawGuides(originalX, y, slotHeight, packview$rowDepth);
        }
        PreviewCache.Preview preview = packview$preview;
        boolean hideBadge = packview$hideBadge;
        packview$preview = null;
        packview$hideBadge = false;
        int stripRight = preview != null
                ? packview$drawPreview(preview, x, y, originalX + listWidth, slotHeight)
                : Integer.MIN_VALUE;
        int badgeWidth = packview$drawBadge(preview != null, hideBadge, stripRight, originalX, y);
        packview$starDrawn = false;
        if (!packview$starShown) {
            return;
        }
        int starLeft = FavoriteStar.left(RowBadge.RIGHT_EDGE, badgeWidth, preview != null);
        int starTop = FavoriteStar.top(preview != null);
        // Like the badge, a strip row keeps its star only where the star clears the strip.
        if (preview != null && !RowLook.labelFits(stripRight, originalX + starLeft + FavoriteStar.SIZE,
                FavoriteStar.SIZE)) {
            return;
        }
        packview$drawStar(originalX + starLeft, y + starTop, packview$starFavorite);
        packview$starLeft = starLeft;
        packview$starTop = starTop;
        packview$starDrawn = true;
    }

    /** Draws the resolution badge right-aligned on the name line; returns its width, 0 when not drawn. */
    @Unique
    private int packview$drawBadge(boolean strip, boolean hideBadge, int stripRight, int originalX, int y) {
        if (hideBadge || !PackView.settings().resolutionLabels) {
            return 0;
        }
        String badge = RowBadge.text((ResourcePackListEntry) (Object) this);
        if (badge.isEmpty()) {
            return 0;
        }
        FontRenderer font = mc.fontRendererObj;
        int right = originalX + RowBadge.RIGHT_EDGE;
        int width = font.getStringWidth(badge);
        // A strip row keeps its badge only where the badge clears the strip; indents push the strip into it.
        if (strip && !RowLook.labelFits(stripRight, right, width)) {
            return 0;
        }
        font.drawStringWithShadow(badge, right - width, y + 1, RowBadge.COLOUR);
        return width;
    }

    /** Gold with a 1 px shadow for a favorite, else the grey outline (edge pixels only). */
    @Unique
    private static void packview$drawStar(int left, int top, boolean favorite) {
        for (int py = 0; py < FavoriteStar.SIZE; py++) {
            for (int px = 0; px < FavoriteStar.SIZE; px++) {
                if (favorite && FavoriteStar.filled(px, py)) {
                    Gui.drawRect(left + px + 1, top + py + 1, left + px + 2, top + py + 2, FavoriteStar.SHADOW);
                }
            }
        }
        for (int py = 0; py < FavoriteStar.SIZE; py++) {
            for (int px = 0; px < FavoriteStar.SIZE; px++) {
                if (favorite ? FavoriteStar.filled(px, py) : FavoriteStar.edge(px, py)) {
                    Gui.drawRect(left + px, top + py, left + px + 1, top + py + 1,
                            favorite ? FavoriteStar.GOLD : FavoriteStar.OUTLINE);
                }
            }
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 2x8 cells of 16 px right of the icon; 12 px when 16 would pass the row edge (deep indents).
     * Cells the pack does not override are drawn dimmed. Returns the strip's right end.
     */
    @Unique
    private int packview$drawPreview(PreviewCache.Preview preview, int x, int y, int rowEdge, int slotHeight) {
        int left = x + PREVIEW_OFFSET;
        int cell = left + PreviewSpec.COLUMNS * PREVIEW_CELL > rowEdge ? PREVIEW_CELL_SMALL : PREVIEW_CELL;
        int top = y + (slotHeight - PreviewSpec.ROWS * cell) / 2;
        int texW = PreviewSpec.atlasWidth(cell);
        int texH = PreviewSpec.atlasHeight(cell);
        mc.getTextureManager().bindTexture(preview.texture);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        for (int i = 0; i < PreviewSpec.CELLS; i++) {
            if (preview.missing(i)) {
                GlStateManager.color(0.5F, 0.5F, 0.5F, 0.6F);
            } else {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
            int u = PreviewSpec.cellX(i, cell);
            int v = PreviewSpec.cellY(i, cell);
            Gui.drawModalRectWithCustomSizedTexture(left + u, top + v, u, v, cell, cell, texW, texH);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        return left + PreviewSpec.COLUMNS * cell;
    }

    /**
     * A click on the star (as last drawn) toggles the favorite and is consumed: returning true stops vanilla's
     * icon-arrow handling, and the list does not select or move the pack. Declared before the relativeX shift so it
     * sees the unshifted relativeX the star was placed by.
     */
    @Inject(method = "mousePressed", at = @At("HEAD"), cancellable = true)
    private void packview$clickStar(int slotIndex, int mouseX, int mouseY, int mouseEvent,
                                         int relativeX, int relativeY, CallbackInfoReturnable<Boolean> cir) {
        if (packview$starDrawn && FavoriteStar.hit(relativeX, relativeY, packview$starLeft,
                packview$starTop)) {
            PackView.settings().toggleFavorite(packview$starKey);
            cir.setReturnValue(true);
        }
    }

    /**
     * Clicks in the indent gutter do nothing. Declared before the relativeX shift below so it runs
     * first at HEAD and still sees the unshifted relativeX.
     */
    @Inject(method = "mousePressed", at = @At("HEAD"), cancellable = true)
    private void packview$ignoreGutterClick(int slotIndex, int mouseX, int mouseY, int mouseEvent,
                                                int relativeX, int relativeY, CallbackInfoReturnable<Boolean> cir) {
        if (relativeX - packview$indentPx() < 0) {
            cir.setReturnValue(false);
        }
    }

    /** Vanilla treats relativeX <= 32 as an icon click; measure from the shifted icon instead. */
    @ModifyVariable(method = "mousePressed", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int packview$shiftRelativeX(int relativeX) {
        return relativeX - packview$indentPx();
    }
}
