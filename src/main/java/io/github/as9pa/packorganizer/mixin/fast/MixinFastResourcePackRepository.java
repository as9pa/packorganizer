package io.github.as9pa.packorganizer.mixin.fast;

import io.github.as9pa.packorganizer.Log;
import io.github.as9pa.packorganizer.fast.FastEntryExtension;
import java.util.List;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refreshes every cached entry key once per scan (keeps vanilla semantics) and logs the scan time. Ported from
 * fastpacks; off when fastpacks is installed.
 */
@Mixin(ResourcePackRepository.class)
public abstract class MixinFastResourcePackRepository {

    @Shadow private List<ResourcePackRepository.Entry> repositoryEntriesAll;

    @Unique private long packorganizer$fastScanStartNanos;

    @Inject(method = "updateRepositoryEntriesAll", at = @At("HEAD"))
    private void packorganizer$fastBeforeScan(CallbackInfo ci) {
        packorganizer$fastScanStartNanos = System.nanoTime();
        for (Object entry : repositoryEntriesAll) {
            if (entry instanceof FastEntryExtension) {
                ((FastEntryExtension) entry).packorganizer$fastRefreshKey();
            }
        }
    }

    @Inject(method = "updateRepositoryEntriesAll", at = @At("RETURN"))
    private void packorganizer$fastAfterScan(CallbackInfo ci) {
        long ms = (System.nanoTime() - packorganizer$fastScanStartNanos) / 1_000_000L;
        Log.LOG.info("Fast loading: rescanned {} packs in {} ms", repositoryEntriesAll.size(), ms);
    }
}
