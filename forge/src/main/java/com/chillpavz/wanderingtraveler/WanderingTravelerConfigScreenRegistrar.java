package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.ClothCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Client only: wires the config screen into Forge's mod-list Config button. Kept in its
 * own class so its client-only references are never loaded on a dedicated server, behind
 * the Dist guard in {@link WanderingTravelerForge}.
 *
 * <p>The screen is Cloth Config's, generated from the same annotated data class Fabric
 * uses; official cloth ships a Forge build at 1.20 and 1.20.1, so no hand-written screen
 * is needed. Cloth is OPTIONAL here, so the button is registered either way and goes
 * nowhere when cloth is absent, which is the least surprising of the available outcomes.
 *
 * <p><b>The {@code BiFunction<Minecraft, Screen, Screen>} form is not a style choice.</b>
 * The shorter {@code Function<Screen, Screen>} constructor does not exist on Forge 46,
 * which is 1.20 and half of what this jar covers, nor on NeoForge 47.1.x, which is the
 * other loader it is tagged for - verified by javap against the 46.0.14 universal jar,
 * where BiFunction is the only constructor. It fails INVISIBLY until someone clicks
 * Config, because the extension point is resolved lazily, so a clean startup proves
 * nothing here.
 */
final class WanderingTravelerConfigScreenRegistrar {

    private WanderingTravelerConfigScreenRegistrar() {
    }

    static void register() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (Minecraft minecraft, Screen parent) -> ClothCompat.screen(parent)));
    }
}
