package io.github.as9pa.packorganizer.mixin;

import io.github.as9pa.packorganizer.EntryExtension;
import io.github.as9pa.packorganizer.PackOrganizer;
import io.github.as9pa.packorganizer.filter.PreviewSpec;
import io.github.as9pa.packorganizer.gui.PreviewCache;
import io.github.as9pa.packorganizer.gui.RowBadge;
import io.github.as9pa.packorganizer.gui.RowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.ResourcePackListEntry;
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
 * ready, the name and description give way to a 2x8 grid of the pack's textures.
 */
@Mixin(ResourcePackListEntry.class)
public abstract class MixinResourcePackListEntry implements RowLayout {
    @Shadow @Final protected Minecraft mc;
    @Shadow @Final protected GuiScreenResourcePacks resourcePacksGUI;

    /** Preview grid start, right of the 32 px icon, from the shifted x. */
    @Unique private static final int PREVIEW_OFFSET = 34;
    @Unique private static final int PREVIEW_CELL = 16;
    @Unique private static final int PREVIEW_CELL_SMALL = 12;

    @Unique private int packorganizer$rowDepth;
    /** Set at drawEntry HEAD, cleared at RETURN: the preview to draw instead of the text, or null. */
    @Unique private PreviewCache.Preview packorganizer$preview;

    @Override
    public void packorganizer$setDepth(int depth) {
        packorganizer$rowDepth = depth;
    }

    @Override
    public int packorganizer$depth() {
        return packorganizer$rowDepth;
    }

    /** Depth only indents the available list; the selected list stays flat. */
    @Unique
    private int packorganizer$indentPx() {
        if (packorganizer$rowDepth <= 0 || resourcePacksGUI.hasResourcePackEntry((ResourcePackListEntry) (Object) this)) {
            return 0;
        }
        return RowLayout.indentPx(packorganizer$rowDepth);
    }

    /** Shifts the whole row right by the indent; the guides are drawn at RETURN. */
    @ModifyVariable(method = "drawEntry", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int packorganizer$indentX(int x) {
        return x + packorganizer$indentPx();
    }

    /**
     * drawEntry uses the literal 157 three times: twice for the name ("if (width > 157)" and
     * "trimStringToWidth(s, 157 - ...)"), once for the description. The name makes room for the
     * badge (when shown) and the indent; the description makes room for the indent only.
     */
    @ModifyConstant(method = "drawEntry",
            constant = {@Constant(intValue = 157, ordinal = 0), @Constant(intValue = 157, ordinal = 1)},
            require = 2)
    private int packorganizer$nameWidth(int vanillaWidth) {
        int width = PackOrganizer.settings().resolutionLabels ? RowBadge.NAME_WIDTH : vanillaWidth;
        return width - packorganizer$indentPx();
    }

    @ModifyConstant(method = "drawEntry", constant = @Constant(intValue = 157, ordinal = 2))
    private int packorganizer$descriptionWidth(int vanillaWidth) {
        return vanillaWidth - packorganizer$indentPx();
    }

    /** isSelected is the hover flag. The first hover starts the background load; text hides once it is ready. */
    @Inject(method = "drawEntry", at = @At("HEAD"))
    private void packorganizer$lookUpPreview(int slotIndex, int x, int y, int listWidth, int slotHeight,
                                             int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        packorganizer$preview = null;
        if (!isSelected || !PackOrganizer.settings().packPreviews
                || !((Object) this instanceof ResourcePackListEntryFound)) {
            return;
        }
        ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) (Object) this).func_148318_i();
        // Defensive: if the Entry mixin did not apply, there is no pack file to read.
        if (pack instanceof EntryExtension && ((EntryExtension) pack).packorganizer$file() != null) {
            packorganizer$preview = PreviewCache.get(((EntryExtension) pack).packorganizer$file());
        }
    }

    /** Both drawStringWithShadow calls (name and description lines) are skipped while the preview shows. */
    @Redirect(method = "drawEntry",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"),
            require = 1)
    private int packorganizer$hideTextForPreview(FontRenderer font, String text, float x, float y, int color) {
        return packorganizer$preview != null ? 0 : font.drawStringWithShadow(text, x, y, color);
    }

    /**
     * x here is the shifted x, so step back by the indent to draw the guides from the original x
     * and keep the badge right-aligned.
     */
    @Inject(method = "drawEntry", at = @At("RETURN"))
    private void packorganizer$drawGuidesAndBadge(int slotIndex, int x, int y, int listWidth, int slotHeight,
                                     int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        int indent = packorganizer$indentPx();
        int originalX = x - indent;
        if (indent > 0) {
            RowLayout.drawGuides(originalX, y, slotHeight, packorganizer$rowDepth);
        }
        PreviewCache.Preview preview = packorganizer$preview;
        packorganizer$preview = null;
        if (preview != null) {
            packorganizer$drawPreview(preview, x, y, originalX + listWidth, slotHeight);
            return;
        }
        if (!PackOrganizer.settings().resolutionLabels) {
            return;
        }
        String badge = RowBadge.text((ResourcePackListEntry) (Object) this);
        if (badge.isEmpty()) {
            return;
        }
        FontRenderer font = mc.fontRendererObj;
        int right = originalX + RowBadge.RIGHT_EDGE;
        font.drawStringWithShadow(badge, right - font.getStringWidth(badge), y + 1, RowBadge.COLOUR);
    }

    /**
     * 2x8 cells of 16 px right of the icon; 12 px when 16 would pass the row edge (deep indents).
     * Cells the pack does not override are drawn dimmed.
     */
    @Unique
    private void packorganizer$drawPreview(PreviewCache.Preview preview, int x, int y, int rowEdge, int slotHeight) {
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
    }

    /**
     * Clicks in the indent gutter do nothing. Declared before the relativeX shift below so it runs
     * first at HEAD and still sees the unshifted relativeX.
     */
    @Inject(method = "mousePressed", at = @At("HEAD"), cancellable = true)
    private void packorganizer$ignoreGutterClick(int slotIndex, int mouseX, int mouseY, int mouseEvent,
                                                int relativeX, int relativeY, CallbackInfoReturnable<Boolean> cir) {
        if (relativeX - packorganizer$indentPx() < 0) {
            cir.setReturnValue(false);
        }
    }

    /** Vanilla treats relativeX <= 32 as an icon click; measure from the shifted icon instead. */
    @ModifyVariable(method = "mousePressed", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int packorganizer$shiftRelativeX(int relativeX) {
        return relativeX - packorganizer$indentPx();
    }
}
