package io.github.as9pa.packview.mixin;

import io.github.as9pa.packview.gui.FilteredList;
import java.util.List;
import net.minecraft.client.gui.GuiResourcePackList;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every GuiSlot draw and mouse path goes through getSize()/getListEntry(). When the toolbar attaches a
 * view, answer from it; the vanilla list itself is never mutated. Only the Available list gets a view.
 */
@Mixin(GuiResourcePackList.class)
public abstract class MixinGuiResourcePackList implements FilteredList {
    @Unique private List<ResourcePackListEntry> packview$view;

    @Override
    public void packview$setView(List<ResourcePackListEntry> view) {
        packview$view = view;
    }

    @Inject(method = "getSize", at = @At("HEAD"), cancellable = true)
    private void packview$filteredSize(CallbackInfoReturnable<Integer> cir) {
        if (packview$view != null) {
            cir.setReturnValue(packview$view.size());
        }
    }

    @Inject(method = "getListEntry(I)Lnet/minecraft/client/resources/ResourcePackListEntry;", at = @At("HEAD"), cancellable = true)
    private void packview$filteredEntry(int index, CallbackInfoReturnable<ResourcePackListEntry> cir) {
        if (packview$view != null) {
            cir.setReturnValue(packview$view.get(index));
        }
    }
}
