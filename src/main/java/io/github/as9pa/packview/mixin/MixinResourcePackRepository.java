package io.github.as9pa.packview.mixin;

import io.github.as9pa.packview.Log;
import io.github.as9pa.packview.PackView;
import io.github.as9pa.packview.filter.NestedPackWalker;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds packs found in subfolders of resourcepacks to the list vanilla builds from the top level. */
@Mixin(ResourcePackRepository.class)
public abstract class MixinResourcePackRepository {
    @Shadow @Final private File dirResourcepacks;

    /**
     * updateRepositoryEntriesAll() builds its entries from this list, and initGui calls it on every open, so the walk
     * runs each time the screen opens: log its cost. Vanilla returns Arrays.asList (fixed size), hence the copy.
     */
    @Inject(method = "getResourcePackFiles", at = @At("RETURN"), cancellable = true)
    private void packview$addNestedPacks(CallbackInfoReturnable<List<File>> cir) {
        if (!PackView.settings().packFolders) {
            return;
        }
        long start = System.nanoTime();
        List<File> nested = NestedPackWalker.find(dirResourcepacks);
        List<File> all = new ArrayList<>(cir.getReturnValue());
        all.addAll(nested);
        cir.setReturnValue(all);
        Log.LOG.info("packview: found {} nested packs in {} ms", nested.size(), (System.nanoTime() - start) / 1_000_000L);
    }
}
