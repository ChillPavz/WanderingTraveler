package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.TravelerClothConfig;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Client only: wires the config screen into Forge's mod-list Config button. Kept in its
 * own class so its client-only references are never loaded on a dedicated server, behind
 * the Dist guard in {@link WanderingTravelerForge}.
 *
 * <p>The screen is Cloth Config's, generated from the same annotated data class Fabric and
 * NeoForge use. Official cloth ships no Forge build at 1.21.11, but the unofficial
 * cloth-config-forge port does, so all three loaders share one implementation instead of
 * this module carrying a hand written screen.
 *
 * <p><b>The {@code BiFunction<Minecraft, Screen, Screen>} form is used deliberately.</b>
 * The shorter {@code Function<Screen, Screen>} constructor only exists from Forge 47, and
 * it is also absent on NeoForge 47.1.x, so the two-argument form is the one that works
 * everywhere this code could be backported to. It fails INVISIBLY until someone clicks
 * Config, so a clean startup proves nothing here.
 */
final class WanderingTravelerConfigScreenRegistrar {

    private WanderingTravelerConfigScreenRegistrar() {
    }

    static void register() {
        // Before anything reads a setting.
        TravelerClothConfig.Plumbing.register();

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> TravelerClothConfig.Plumbing.screen(parent)));
    }
}
