package io.github.as9pa.packview.mixin;

import io.github.as9pa.packview.filter.PackPaths;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shows a nested pack under its file name only; its full relative path is the identity, not the label.
 * In the Selected list, where there are no folder rows, the folder goes in the description instead.
 */
@Mixin(ResourcePackListEntryFound.class)
public abstract class MixinResourcePackListEntryFound extends ResourcePackListEntry {
    @Shadow @Final private ResourcePackRepository.Entry field_148319_c;

    private MixinResourcePackListEntryFound(GuiScreenResourcePacks screen) {
        super(screen);
    }

    /** func_148312_b() is the row name, vanilla's field_148319_c.getResourcePackName(). */
    @Inject(method = "func_148312_b", at = @At("HEAD"), cancellable = true)
    private void packview$shortName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(PackPaths.shortName(field_148319_c.getResourcePackName()));
    }

    /**
     * func_148311_a() is the description. Vanilla draws at most two wrapped lines of it under the name,
     * so a gray folder line goes first and the description keeps the line after it.
     */
    @Inject(method = "func_148311_a", at = @At("HEAD"), cancellable = true)
    private void packview$folderLine(CallbackInfoReturnable<String> cir) {
        String folder = PackPaths.folderOf(field_148319_c.getResourcePackName());
        if (!folder.isEmpty() && resourcePacksGUI.hasResourcePackEntry(this)) {
            cir.setReturnValue("§7" + folder + "\n" + field_148319_c.getTexturePackDescription());
        }
    }
}
