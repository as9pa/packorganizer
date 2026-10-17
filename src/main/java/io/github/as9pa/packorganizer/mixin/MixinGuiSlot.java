package io.github.as9pa.packorganizer.mixin;

import io.github.as9pa.packorganizer.PackOrganizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiResourcePackList;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transparent background for the pack lists: swaps the dirt container for a translucent fill, drops the
 * dirt top/bottom overlays, and scissors the rows to the list so they do not bleed over the toolbar.
 * GuiSlot is shared with every list in the game, so each hook only acts on GuiResourcePackList.
 * Every hook except the RETURN safety net is optional (require = 0): a mod that rewrites GuiSlot.drawScreen
 * (OptiFine) then costs the effect, not the game's startup.
 */
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot {
    @Shadow @Final protected Minecraft mc;
    @Shadow public int top;
    @Shadow public int bottom;
    @Shadow public int left;
    @Shadow public int right;

    @Unique private boolean packorganizer$scissoring;

    @Unique
    private boolean packorganizer$transparent() {
        return (Object) this instanceof GuiResourcePackList && PackOrganizer.settings().transparentBackground;
    }

    // drawContainerBackground is added by Forge, so it has no SRG name and must not be remapped.
    @Inject(method = "drawContainerBackground(Lnet/minecraft/client/renderer/Tessellator;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void packorganizer$translucentContainer(Tessellator tessellator, CallbackInfo ci) {
        if (packorganizer$transparent()) {
            Gui.drawRect(left, top, right, bottom, 0x80000000);
            ci.cancel();
        }
    }

    @Inject(method = "overlayBackground(IIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void packorganizer$skipOverlay(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
        if (packorganizer$transparent()) {
            ci.cancel();
        }
    }

    @Inject(method = "drawScreen(IIF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiSlot;drawContainerBackground(Lnet/minecraft/client/renderer/Tessellator;)V", shift = At.Shift.AFTER, remap = false), require = 0)
    private void packorganizer$startScissor(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!packorganizer$transparent()) {
            return;
        }
        // Without the dirt overlays nothing hides rows scrolled past top/bottom, so clip them in GL.
        int scale = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left * scale, mc.displayHeight - bottom * scale, (right - left) * scale, (bottom - top) * scale);
        packorganizer$scissoring = true;
    }

    @Inject(method = "drawScreen(IIF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiSlot;drawSelectionBox(IIII)V", shift = At.Shift.AFTER), require = 0)
    private void packorganizer$endScissor(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // Keyed on the flag, not the setting, so a toggle between the two hooks cannot leave scissor on.
        if (packorganizer$scissoring) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            packorganizer$scissoring = false;
        }
    }

    /** Safety net: if the end-scissor hook did not apply, scissor still never outlives the list. */
    @Inject(method = "drawScreen(IIF)V", at = @At("RETURN"))
    private void packorganizer$scissorOffAtReturn(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        packorganizer$endScissor(mouseX, mouseY, partialTicks, ci);
    }
}
