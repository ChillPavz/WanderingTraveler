package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.title.TitleArt;
import com.chillpavz.wanderingtraveler.title.TitleRenderer;
import com.chillpavz.wanderingtraveler.title.TitleTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
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
        // The title images have to be DISCOVERED rather than listed: a missing texture
        // draws as the magenta box, so the mod must know what exists before it asks.
        // Forge's registration takes no id, unlike NeoForge's addListener(Identifier, ..).
        event.registerReloadListener((ResourceManagerReloadListener) TitleArt::reload);
    }

    private static void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
        // THE VANILLA HUD IS A TREE, AND minecraft:title IS NOT IN ITS ROOT. Forge builds
        // the root out of two SUB-STACKS, pre_sleep_phase and post_sleep_phase, and the
        // title layer is added inside post_sleep_phase. Read out of ForgeLayeredDraw.init.
        //
        // That is why the three argument addBelow silently did nothing: it uses the
        // receiver's own name as the stack, so it added the layer to the ROOT and then
        // tried to order it against a target one level down. Nothing warned, and the
        // layer was simply never drawn - the tracker ran, a title was queued, and the
        // render was never called.
        //
        // The four argument form names the stack, so this adds the layer INTO
        // post_sleep_phase directly below minecraft:title. Order is
        // (stack, newId, target, layer): its lambda warns when the TARGET name is still
        // available, then does add(newId, layer) followed by move(newId, target, BELOW).
        // Below the title, so a /title from a datapack or another mod still draws over
        // ours. ForgeLayer's single method is render(GuiGraphics, DeltaTracker), which is
        // exactly TitleRenderer::render.
        event.getLayeredDraw().addBelow(
                ForgeLayeredDraw.POST_SLEEP_STACK,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "biome_title"),
                ForgeLayeredDraw.TITLE_OVERLAY,
                TitleRenderer::render);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        TitleTracker.tick(Minecraft.getInstance());
    }
}
