package com.chillpavz.wanderingtraveler.config;

import com.chillpavz.wanderingtraveler.Constants;
import com.chillpavz.wanderingtraveler.platform.Services;
import net.minecraft.client.gui.screens.Screen;

/**
 * The only place in this module that is allowed to reach Cloth Config.
 *
 * <h2>Why this class exists on this band and on no other</h2>
 * Cloth is a REQUIRED dependency on every other branch and an OPTIONAL one here, and that
 * is forced rather than preferred. Cloth ships a Fabric file and a Forge file at 1.20 and
 * 1.20.1 and <b>no neoforge-tagged file at all</b> - verified against the Modrinth API per
 * game version, not assumed. This band's Forge jar is also the NeoForge jar, because
 * NeoForge 47.1.x provides the {@code forge} mod id, so a NeoForge player installing this
 * mod is never offered cloth and the launcher cannot fetch it for them. With cloth
 * required their game would refuse to start with a MISSING dependency, which is correct
 * behaviour and still looks exactly like a broken mod.
 *
 * <p>So the mod degrades to "no config screen" instead. The titles are the product and
 * they work perfectly on the defaults; the config FILE is not written either, so nothing
 * is silently half-applied.
 *
 * <h2>Why the guard is a mod-loaded check and not just a try/catch</h2>
 * {@link TravelerClothConfig} implements a Cloth interface, so merely loading that class
 * needs Cloth on the classpath. A try/catch inside it would still be entered through a
 * method whose verification can trigger that load, which is the kind of thing that works
 * on one JVM and not the next. Asking the loader first means no Cloth-referencing
 * bytecode is ever reached when Cloth is absent. The try/catch is kept as well, for the
 * case where Cloth is present but broken.
 */
public final class ClothCompat {

    /**
     * Every id Cloth answers to, because it is not the same one on both loaders.
     *
     * <p>Read out of the real 11.1.136 artifacts: the Fabric build is {@code cloth-config}
     * and PROVIDES {@code cloth-config2}, while the Forge build is {@code cloth_config}.
     * Checking all three costs nothing and means the guard cannot be defeated by the
     * spelling.
     */
    private static final String[] IDS = {"cloth-config", "cloth_config", "cloth-config2"};

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
