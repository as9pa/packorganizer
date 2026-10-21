package io.github.as9pa.packview.mixin;

import io.github.as9pa.packview.EntryExtension;
import io.github.as9pa.packview.filter.PackColors;
import io.github.as9pa.packview.filter.PackPaths;
import io.github.as9pa.packview.filter.PackResolution;
import io.github.as9pa.packview.filter.PackScan;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives each pack entry a resolution, filled in by a background scan, and names nested packs by their path relative to
 * resourcepacks.
 */
@Mixin(ResourcePackRepository.Entry.class)
public abstract class MixinResourcePackRepositoryEntry implements EntryExtension {

    @Shadow @Final private File resourcePackFile;

    @Unique private volatile PackResolution packview$resolution;
    @Unique private volatile PackColors packview$colors;

    /**
     * updateRepositoryEntriesAll() calls updateResourcePack() once for each pack it has not seen before (at startup,
     * and for new or changed files on every rescan), so this queues one scan per real entry. The throwaway entries it
     * builds only for comparison never get here. An @Inject, not a @Redirect, so it stacks with other mods (fastpacks)
     * that redirect the getPackImage() call inside this method.
     */
    @Inject(method = "updateResourcePack", at = @At("RETURN"))
    private void packview$queueScan(CallbackInfo ci) {
        // mcDataDir is set in Minecraft's constructor, so it is valid even during startGame.
        PackScan.configure(new File(Minecraft.getMinecraft().mcDataDir, "config" + File.separator + "packview"));
        PackScan.submit(resourcePackFile, this);
    }

    /**
     * A nested pack's identity is its path relative to resourcepacks ("pvp/16x/Pack.zip"): options.txt matching, the
     * list written on Done and the row name all read this. Top-level packs (and anything outside resourcepacks) fall
     * through to vanilla's file name, so their names never change.
     */
    @Inject(method = "getResourcePackName", at = @At("HEAD"), cancellable = true)
    private void packview$relativeName(CallbackInfoReturnable<String> cir) {
        // Not through the entry's outer-instance field: that synthetic field is field_110525_a in the dev jar but
        // keeps its obfuscated name in the real game, where shadowing it crashed startup. options.txt matching runs in
        // the repository's constructor, before Minecraft holds it, so fall back to the folder startGame passes it.
        Minecraft mc = Minecraft.getMinecraft();
        ResourcePackRepository repository = mc.getResourcePackRepository();
        File root = repository != null ? repository.getDirResourcepacks() : new File(mc.mcDataDir, "resourcepacks");
        File parent = resourcePackFile.getAbsoluteFile().getParentFile();
        if (parent == null || parent.equals(root.getAbsoluteFile())
                || !resourcePackFile.getAbsolutePath().startsWith(root.getAbsolutePath() + File.separator)) {
            return;
        }
        cir.setReturnValue(PackPaths.relative(root, resourcePackFile));
    }

    /**
     * Vanilla equals compares toString(), which is "name:type:mtime" (fastpacks caches the same string), so two nested
     * packs with the same file name and mtime in different folders would collide. Different files are never equal;
     * the same file falls through, keeping the mtime check. Stricter than vanilla, so hashCode stays consistent.
     */
    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void packview$differentFileNotEqual(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (other instanceof EntryExtension && !resourcePackFile.equals(((EntryExtension) other).packview$file())) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public File packview$file() {
        return resourcePackFile;
    }

    @Override
    public void acceptResolution(PackResolution resolution) {
        packview$resolution = resolution;
    }

    @Override
    public PackResolution packview$resolution() {
        return packview$resolution;
    }

    @Override
    public void acceptColors(PackColors colors) {
        packview$colors = colors;
    }

    @Override
    public PackColors packview$colors() {
        return packview$colors;
    }
}
