package io.github.as9pa.packorganizer;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = PackOrganizer.MODID, version = PackOrganizer.VERSION, useMetadata = true, clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]")
public class PackOrganizer {
    public static final String MODID = "packorganizer";
    /** Keep in step with gradle.properties. */
    public static final String VERSION = "0.1.0";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Log.LOG.info("packorganizer initialised");
    }
}
