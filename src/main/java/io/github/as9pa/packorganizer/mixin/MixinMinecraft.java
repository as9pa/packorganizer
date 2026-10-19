package io.github.as9pa.packorganizer.mixin;

import io.github.as9pa.packorganizer.PackOrganizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lifts vanilla's 30 fps menu cap on the Resource Packs screen while the player preview is on, so the
 * animated model runs at the user's Max Framerate when the screen is opened from the title screen.
 * getLimitFramerate returns 30 whenever no world is loaded and a screen is open; here it returns the
 * setting instead, which is unlimited at the slider max (isFramerateLimitBelowMax then turns false as usual).
 * Optional (require = 0): if another mod removes or renames the method, the menu keeps its cap and the game still starts.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true, require = 0)
    private void packorganizer$uncapPackScreen(CallbackInfoReturnable<Integer> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.currentScreen instanceof GuiScreenResourcePacks && PackOrganizer.settings().playerPreview) {
            cir.setReturnValue(mc.gameSettings.limitFramerate);
        }
    }
}
