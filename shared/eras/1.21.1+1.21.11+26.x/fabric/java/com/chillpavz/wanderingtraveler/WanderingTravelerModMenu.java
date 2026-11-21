package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.TravelerClothConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

/**
 * The Config button beside the mod in Mod Menu's list.
 *
 * <p>Fabric has no config-screen hook of its own, so this is the only way a Fabric player
 * reaches the screen from the menu; NeoForge has one built in and uses it instead. Mod
 * Menu is therefore an OPTIONAL dependency: without it the config file still works, the
 * screen simply has no button, and this class is never loaded because nothing declares
 * the entrypoint's interface until Mod Menu does.
 */
public class WanderingTravelerModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Named rather than returned inline: the interface's type parameter is a
        // wildcard here, and javac cannot infer a method reference against one.
        ConfigScreenFactory<Screen> factory = TravelerClothConfig.Plumbing::screen;
        return factory;
    }
}
