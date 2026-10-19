package io.github.as9pa.packorganizer.fast;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.junit.Test;

public class FastpacksDetectorTest {

    private static Function<String, URL> resources(String... names) {
        final Set<String> present = new HashSet<>(Arrays.asList(names));
        return name -> {
            if (!present.contains(name)) {
                return null;
            }
            try {
                return new URL("file:/fake/" + name);
            } catch (MalformedURLException e) {
                throw new AssertionError(e);
            }
        };
    }

    @Test public void absentWhenNoMarkerResource() {
        assertFalse(FastpacksDetector.fastpacksPresent(resources("mixins.packorganizer.json")));
    }

    @Test public void presentWhenMixinConfigFound() {
        assertTrue(FastpacksDetector.fastpacksPresent(resources("mixins.fastpacks.json")));
    }

    @Test public void presentWhenPluginClassFound() {
        assertTrue(FastpacksDetector.fastpacksPresent(
                resources("io/github/as9pa/fastpacks/mixin/FastPacksMixinPlugin.class")));
    }

    @Test public void classLoaderOverloadFindsResource() throws Exception {
        Path dir = Files.createTempDirectory("fastpacks-detector");
        Files.write(dir.resolve("mixins.fastpacks.json"), new byte[0]);
        try (URLClassLoader withFastpacks = new URLClassLoader(new URL[] {dir.toUri().toURL()}, null);
                URLClassLoader without = new URLClassLoader(new URL[0], null)) {
            assertTrue(FastpacksDetector.fastpacksPresent(withFastpacks));
            assertFalse(FastpacksDetector.fastpacksPresent(without));
        } finally {
            Files.delete(dir.resolve("mixins.fastpacks.json"));
            Files.delete(dir);
        }
    }

    @Test public void baselineOnlyWhenTrue() {
        assertTrue(FastpacksDetector.baseline("true"));
        assertTrue(FastpacksDetector.baseline("TRUE"));
        assertFalse(FastpacksDetector.baseline("false"));
        assertFalse(FastpacksDetector.baseline(null));
        assertFalse(FastpacksDetector.baseline(""));
    }
}
