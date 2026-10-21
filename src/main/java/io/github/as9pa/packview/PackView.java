package io.github.as9pa.packview;

import io.github.as9pa.packview.gui.preview.DevShots;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = PackView.MODID, version = PackView.VERSION, useMetadata = true, clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]", guiFactory = "io.github.as9pa.packview.gui.SettingsGuiFactory")
public class PackView {
    public static final String MODID = "packview";
    /** Keep in step with gradle.properties. */
    public static final String VERSION = "0.4.0";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Log.LOG.info("packview initialised");
        DevShots.init();
    }

    /**
     * The user settings, loading them on first use. Read settings through this, not Settings.get(),
     * because the repository can ask during startGame, before any FML event.
     */
    public static Settings settings() {
        // mcDataDir is set in Minecraft's constructor, so it is valid even during startGame.
        File dataDir = Minecraft.getMinecraft().mcDataDir;
        File configDir = new File(dataDir, "config" + File.separator + MODID);
        if (!configMigrated) {
            migrateConfig(dataDir, configDir);
        }
        Settings.init(configDir);
        return Settings.get();
    }

    private static volatile boolean configMigrated;

    /**
     * Copies config/packorganizer over before the first settings load. This is also the first thing to touch
     * config/packview: the repository's getResourcePackFiles mixin asks for settings before any entry queues a scan,
     * so the scan cache (packs.json) is configured only after this has run.
     */
    private static synchronized void migrateConfig(File dataDir, File configDir) {
        if (configMigrated) {
            return;
        }
        ConfigMigration.migrate(
                new File(dataDir, "config" + File.separator + ConfigMigration.OLD_MODID).toPath(), configDir.toPath());
        configMigrated = true;
    }
}
