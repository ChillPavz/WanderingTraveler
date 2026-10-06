package com.chillpavz.wanderingtraveler.platform;

import com.chillpavz.wanderingtraveler.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {

        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        // STATIC at NeoForge 21.6. fancymodloader later moved isProduction onto an
        // instance reached through FMLLoader.getCurrent(), which is what the 1.21.11
        // and 26.x branches call. Compiling against the oldest build in the band is
        // what surfaces this rather than a NoSuchMethodError in somebody's game.
        return !FMLLoader.isProduction();
    }
}