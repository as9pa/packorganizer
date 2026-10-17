package io.github.as9pa.packorganizer;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = PackOrganizer.MODID, version = PackOrganizer.VERSION, useMetadata = true, clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]", guiFactory = "io.github.as9pa.packorganizer.gui.SettingsGuiFactory")
public class PackOrganizer {
    public static final String MODID = "packorganizer";
    /** Keep in step with gradle.properties. */
    public static final String VERSION = "0.2.1";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Log.LOG.info("packorganizer initialised");
    }

    /**
     * The user settings, loading them on first use. Read settings through this, not Settings.get(),
     * because the repository can ask during startGame, before any FML event.
     */
    public static Settings settings() {
        // mcDataDir is set in Minecraft's constructor, so it is valid even during startGame.
        Settings.init(new File(Minecraft.getMinecraft().mcDataDir, "config" + File.separator + MODID));
        return Settings.get();
    }
}
