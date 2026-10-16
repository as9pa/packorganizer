package io.github.as9pa.packorganizer.mixin;

import io.github.as9pa.packorganizer.EntryExtension;
import io.github.as9pa.packorganizer.filter.PackResolution;
import io.github.as9pa.packorganizer.filter.PackScan;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives each pack entry a resolution, filled in by a background scan. */
@Mixin(ResourcePackRepository.Entry.class)
public abstract class MixinResourcePackRepositoryEntry implements EntryExtension {

    @Shadow @Final private File resourcePackFile;

    @Unique private volatile PackResolution packorganizer$resolution;

    /**
     * updateRepositoryEntriesAll() calls updateResourcePack() once for each pack it has not seen before (at startup,
     * and for new or changed files on every rescan), so this queues one scan per real entry. The throwaway entries it
     * builds only for comparison never get here. An @Inject, not a @Redirect, so it stacks with other mods (fastpacks)
     * that redirect the getPackImage() call inside this method.
     */
    @Inject(method = "updateResourcePack", at = @At("RETURN"))
    private void packorganizer$queueScan(CallbackInfo ci) {
        // mcDataDir is set in Minecraft's constructor, so it is valid even during startGame.
        PackScan.configure(new File(Minecraft.getMinecraft().mcDataDir, "config" + File.separator + "packorganizer"));
        PackScan.submit(resourcePackFile, this);
    }

    @Override
    public void acceptResolution(PackResolution resolution) {
        packorganizer$resolution = resolution;
    }

    @Override
    public PackResolution packorganizer$resolution() {
        return packorganizer$resolution;
    }
}
