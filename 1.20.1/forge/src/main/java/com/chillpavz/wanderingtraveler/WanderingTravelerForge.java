package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.ClothCompat;
import com.chillpavz.wanderingtraveler.title.TitleArt;
import com.chillpavz.wanderingtraveler.title.TitleRenderer;
import com.chillpavz.wanderingtraveler.title.TitleTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint, and also the NeoForge one.
 *
 * <h2>One jar, two loaders</h2>
 * NeoForge at 1.20 and 1.20.1 is 47.1.x: a soft fork still on {@code net.minecraftforge}
 * packages that PROVIDES the {@code forge} mod id, so this file loads there unchanged and
 * the upload is tagged for both. There is no separate NeoForge module on this branch, and
 * that is the reason the config-screen constructor and the cloth dependency below are
 * both chosen for the NeoForge case rather than the Forge one.
 *
 * <h2>The HUD API here is NOT the one any other branch uses</h2>
 * Forge 46 and 47 have {@link RegisterGuiOverlaysEvent} plus {@code IGuiOverlay}: a flat
 * list of named overlays with {@code registerBelow(target, id, overlay)}, where the target
 * is a {@code ResourceLocation} taken from {@link VanillaGuiOverlay}. The nested
 * {@code ForgeLayeredDraw} form the 1.21.1 module uses - with its four-argument addBelow
 * and its sub-stacks - does not arrive until Forge 52.1.2, and the whole overlay package
 * disappears again at Forge 51 and 56. Both majors this jar covers were checked by javap
 * against their universal jars.
 *
 * <p>{@code IGuiOverlay.render} hands over {@code (ForgeGui, GuiGraphics, float, int, int)}
 * where the float is the partial tick, so the shared renderer's own
 * {@code (GuiGraphics, float)} signature is exactly what Fabric's HudRenderCallback wants
 * too and one method serves both loaders.
 *
 * <h2>Forge 46 and 47 are EventBus 6</h2>
 * There are no static {@code BUS} fields. The mod bus comes from
 * {@link FMLJavaModLoadingContext} and the game bus is {@link MinecraftForge#EVENT_BUS}.
 * Both the HUD and reload events implement {@code IModBusEvent} and so go on the MOD bus,
 * while the tick event is a game-bus event with a {@code phase} field rather than nested
 * Pre and Post types. Getting a bus wrong fails SILENTLY at runtime.
 */
@Mod(Constants.MOD_ID)
public class WanderingTravelerForge {

    public WanderingTravelerForge() {
        WanderingTraveler.init();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(WanderingTravelerForge::onRegisterReloadListeners);
        modBus.addListener(WanderingTravelerForge::onRegisterGuiOverlays);

        MinecraftForge.EVENT_BUS.addListener(WanderingTravelerForge::onClientTick);

        // Client only. The config screen classes must never be loaded on a dedicated
        // server, so the reference sits behind this guard in a separate class.
        // FMLEnvironment.dist is a FIELD on classic Forge.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Before anything reads a setting, and only if cloth is actually installed -
            // it is OPTIONAL on this band. See ClothCompat for why.
            ClothCompat.register();
            WanderingTravelerConfigScreenRegistrar.register();
        }
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // The title glyphs have to be DISCOVERED rather than listed: an unknown font id is
        // a silent no-op in vanilla, so the mod must know what exists before it asks.
        event.registerReloadListener((ResourceManagerReloadListener) TitleArt::reload);
    }

    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        // Below the vanilla title overlay, so a /title from a datapack or another mod
        // still draws over ours rather than being hidden by it.
        //
        // Argument order is (target, id, overlay) and the target is a ResourceLocation,
        // which is the opposite way round from the 1.21.1 module's four-argument call.
        // The id is a bare string here: Forge namespaces it with the mod id itself.
        event.registerBelow(VanillaGuiOverlay.TITLE_TEXT.id(), "biome_title",
                (gui, graphics, partialTick, screenWidth, screenHeight)
                        -> TitleRenderer.render(graphics, partialTick));
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        // One tick per game tick: this event fires twice, once per phase.
        if (event.phase == TickEvent.Phase.END) {
            TitleTracker.tick(Minecraft.getInstance());
        }
    }
}
