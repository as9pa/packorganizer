package io.github.as9pa.packorganizer.fast;

import java.net.URL;
import java.util.function.Function;

/**
 * Decides whether packorganizer's built-in fast-loading mixins should run. They switch off when fastpacks is installed
 * (it does the same work) or when -Dpackorganizer.baseline=true asks for vanilla behaviour, for timing comparisons.
 * Looks for resources only, never loads a class, so it is safe from the Mixin config plugin.
 */
public final class FastpacksDetector {
    public static final String BASELINE_PROPERTY = "packorganizer.baseline";
    static final String[] FASTPACKS_RESOURCES = {
            "mixins.fastpacks.json",
            "io/github/as9pa/fastpacks/mixin/FastPacksMixinPlugin.class"};

    private FastpacksDetector() {
    }

    /** True when any fastpacks marker resource is visible through {@code getResource}. */
    public static boolean fastpacksPresent(Function<String, URL> getResource) {
        for (String name : FASTPACKS_RESOURCES) {
            if (getResource.apply(name) != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean fastpacksPresent(final ClassLoader loader) {
        return fastpacksPresent(loader::getResource);
    }

    /** The value of -Dpackorganizer.baseline, passed in so tests need not touch system properties. */
    public static boolean baseline(String propertyValue) {
        return Boolean.parseBoolean(propertyValue);
    }
}
