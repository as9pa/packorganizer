package io.github.as9pa.packorganizer.mixin;

import io.github.as9pa.packorganizer.Log;
import io.github.as9pa.packorganizer.fast.FastpacksDetector;
import java.util.List;
import java.util.Set;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Turns the built-in fast-loading mixins (mixin.fast) off when fastpacks is installed, so the two never patch the same
 * methods twice, or when -Dpackorganizer.baseline=true. Must not reference Minecraft classes.
 */
public class PackOrganizerMixinPlugin implements IMixinConfigPlugin {
    private static final String FAST_PACKAGE = "io.github.as9pa.packorganizer.mixin.fast.";

    private Boolean fastEnabled;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.startsWith(FAST_PACKAGE) || fastEnabled();
    }

    /**
     * Checked on first use, not in onLoad: target classes load only after every tweaker jar is on the classpath, so by
     * then fastpacks is visible if it is installed.
     */
    private boolean fastEnabled() {
        if (fastEnabled == null) {
            if (FastpacksDetector.baseline(System.getProperty(FastpacksDetector.BASELINE_PROPERTY))) {
                Log.LOG.warn("Fast loading: BASELINE mode, built-in fast loading disabled");
                fastEnabled = false;
            } else if (FastpacksDetector.fastpacksPresent(loader())) {
                Log.LOG.info("Fast loading: fastpacks is installed, using it instead");
                fastEnabled = false;
            } else {
                Log.LOG.info("Fast loading: built in");
                fastEnabled = true;
            }
        }
        return fastEnabled;
    }

    private static ClassLoader loader() {
        ClassLoader loader = Launch.classLoader;
        return loader != null ? loader : PackOrganizerMixinPlugin.class.getClassLoader();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
