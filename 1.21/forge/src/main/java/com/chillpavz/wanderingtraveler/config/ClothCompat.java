package com.chillpavz.wanderingtraveler.config;

import com.chillpavz.wanderingtraveler.Constants;
import com.chillpavz.wanderingtraveler.platform.Services;
import net.minecraft.client.gui.screens.Screen;

/**
 * The only place in this module that is allowed to reach Cloth Config.
 *
 * <h2>Why Cloth is optional on Forge</h2>
 * No single Cloth project serves Forge on every version the Forge build covers: official
 * cloth ships Forge files at 1.20.1 and 1.21.1 only, and from 1.21.5 up the Forge build is
 * the separate cloth-config-forge port. Both carry the {@code cloth_config} mod id, but a
 * launcher only installs what the store listing names, and one listing cannot name the
 * right project per version. So every Forge build lists both as optional and degrades to
 * "no config screen" without either. The titles are the product and they work on the
 * defaults; the config FILE is not written either, so nothing is silently half-applied.
 *
 * <h2>Why the guard is a mod-loaded check and not just a try/catch</h2>
 * {@link TravelerClothConfig} implements a Cloth interface, so merely loading that class
 * needs Cloth on the classpath. Asking the loader first means no Cloth-referencing
 * bytecode is ever reached when Cloth is absent. The try/catch is kept as well, for the
 * case where Cloth is present but broken.
 */
public final class ClothCompat {

    /**
     * Every id Cloth answers to. The Forge builds, official and port alike, are
     * {@code cloth_config}; the Fabric spellings cost nothing and mean the guard cannot be
     * defeated by the spelling.
     */
    private static final String[] IDS = {"cloth_config", "cloth-config", "cloth-config2"};

    private static boolean available;

    private ClothCompat() {
    }

    /** True once {@link #register()} has found Cloth and set the config up. */
    public static boolean isAvailable() {
        return available;
    }

    /** Load the config and push it into the mod, if Cloth is here at all. */
    public static void register() {
        if (!installed()) {
            Constants.LOG.info("Cloth Config is not installed; running on the default "
                    + "settings with no config screen");
            return;
        }
        try {
            TravelerClothConfig.Plumbing.register();
            available = true;
        } catch (Throwable t) {
            // Present but unusable. One line, and the mod carries on.
            Constants.LOG.error("Cloth Config is installed but could not be set up; "
                    + "running on the default settings", t);
        }
    }

    /** The config screen, or the screen the player came from when there is none. */
    public static Screen screen(Screen parent) {
        if (!available) {
            return parent;
        }
        try {
            return TravelerClothConfig.Plumbing.screen(parent);
        } catch (Throwable t) {
            Constants.LOG.error("Could not open the config screen", t);
            return parent;
        }
    }

    private static boolean installed() {
        for (String id : IDS) {
            if (Services.PLATFORM.isModLoaded(id)) {
                return true;
            }
        }
        return false;
    }
}
