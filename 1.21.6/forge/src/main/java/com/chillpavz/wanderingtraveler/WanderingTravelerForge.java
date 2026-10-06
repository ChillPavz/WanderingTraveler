package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.title.TitleArt;
import com.chillpavz.wanderingtraveler.title.TitleRenderer;
import com.chillpavz.wanderingtraveler.title.TitleTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint.
 *
 * <p>Forge 61 is on EventBus 8, which is a different shape from both older Forge and from
 * NeoForge: {@code @Mod} takes a NO-ARG constructor, and there is no {@code IEventBus} and
 * no {@code FMLJavaModLoadingContext}.
 *
 * <p><b>Every event this class uses is a GAME bus event, so every one is subscribed
 * through its static {@code BUS} field.</b> That was checked rather than assumed, because
 * putting a listener on the wrong bus fails SILENTLY at runtime. The discriminator is
 * {@code IModBusEvent}: {@code RegisterEvent} implements it and has no static {@code BUS},
 * while {@link RegisterClientReloadListenersEvent}, {@link AddGuiOverlayLayersEvent} and
 * {@link TickEvent.ClientTickEvent.Post} implement none of it and all expose {@code BUS}.
 *
 * <p>The Forge names differ from NeoForge's for the same two jobs:
 * {@code AddClientReloadListenersEvent} is {@link RegisterClientReloadListenersEvent}
 * here, and {@code RegisterGuiLayersEvent} is {@link AddGuiOverlayLayersEvent}.
 */
@Mod(Constants.MOD_ID)
public class WanderingTravelerForge {

    public WanderingTravelerForge() {
        WanderingTraveler.init();

        RegisterClientReloadListenersEvent.BUS.addListener(
                WanderingTravelerForge::onRegisterReloadListeners);
        AddGuiOverlayLayersEvent.BUS.addListener(WanderingTravelerForge::onAddGuiLayers);
        TickEvent.ClientTickEvent.Post.BUS.addListener(WanderingTravelerForge::onClientTick);

        // Client only. The config screen classes must never be loaded on a dedicated
        // server, so the reference sits behind this guard in a separate class.
        // FMLEnvironment.dist is a FIELD on classic Forge; NeoForge 26.x made it the
        // method getDist().
        if (FMLEnvironment.dist == Dist.CLIENT) {
            WanderingTravelerConfigScreenRegistrar.register();
        }
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // The title glyphs have to be DISCOVERED rather than listed: an unknown font id is
        // a silent no-op in vanilla, so the mod must know what exists before it asks.
        // Forge's registration takes no id, unlike NeoForge's addListener(ResourceLocation, ..).
        event.registerReloadListener((ResourceManagerReloadListener) TitleArt::reload);
    }

    private static void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
        // Below the vanilla title layer, so a /title from a datapack or another mod still
        // draws over ours rather than being hidden by it. ForgeLayer's single method is
        // render(GuiGraphics, DeltaTracker), which is exactly TitleRenderer::render.
        //
        // ARGUMENT ORDER IS (NEW ID, TARGET, LAYER), AND THE OPPOSITE ORDER FAILS QUIETLY.
        // NeoForge's registerBelow takes the target first, so the two read backwards from
        // each other. Getting it wrong here does not throw: Forge logs one line, "Expected
        // layer <yours> was not found in stack minecraft:vanilla_root, no layer
        // modifications have been made", and the HUD element is simply never added, so
        // nothing draws at all - not even the plain text path.
        //
        // Read out of the bytecode rather than guessed: the four argument form is
        // (stack, newId, target, layer), and its lambda warns when the TARGET name is
        // still available, then does add(newId, layer) followed by move(newId, target,
        // BELOW).
        event.getLayeredDraw().addBelow(
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "biome_title"),
                ForgeLayeredDraw.TITLE_OVERLAY,
                TitleRenderer::render);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        TitleTracker.tick(Minecraft.getInstance());
    }
}
