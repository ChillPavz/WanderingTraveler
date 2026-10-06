package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.ClothCompat;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Client only: wires the config screen into Forge's mod-list Config button. Kept in its
 * own class so its client-only references are never loaded on a dedicated server, behind
 * the Dist guard in {@link WanderingTravelerForge}.
 *
 * <p>The screen is Cloth Config's, generated from the same annotated data class Fabric and
 * NeoForge use. Cloth is OPTIONAL on Forge (see {@link ClothCompat}), so the button is
 * registered either way and goes nowhere when Cloth is absent.
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
        // Before anything reads a setting, and only if Cloth is actually installed.
        ClothCompat.register();

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> ClothCompat.screen(parent)));
    }
}
