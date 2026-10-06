package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.ClothCompat;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

/**
 * The Config button beside the mod in Mod Menu's list.
 *
 * <p>Fabric has no config-screen hook of its own, so this is the only way a Fabric player
 * reaches the screen from the menu. Mod Menu is therefore an OPTIONAL dependency: without
 * it the config file still works, the screen simply has no button, and this class is never
 * loaded because nothing declares the entrypoint's interface until Mod Menu does.
 *
 * <p>Goes through {@link ClothCompat} rather than straight to the Cloth class, because
 * Cloth is optional on this band as well: with Mod Menu installed and Cloth absent the
 * button is still built, and it simply returns the player to where they came from.
 */
public class WanderingTravelerModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Named rather than returned inline: the interface's type parameter is a
        // wildcard here, and javac cannot infer a method reference against one.
        ConfigScreenFactory<Screen> factory = ClothCompat::screen;
        return factory;
    }
}
