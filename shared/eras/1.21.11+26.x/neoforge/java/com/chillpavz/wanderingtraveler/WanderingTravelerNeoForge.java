package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.TravelerClothConfig;
import com.chillpavz.wanderingtraveler.title.TitleArt;
import com.chillpavz.wanderingtraveler.title.TitleRenderer;
import com.chillpavz.wanderingtraveler.title.TitleTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class WanderingTravelerNeoForge {

    public WanderingTravelerNeoForge(IEventBus modBus, ModContainer container) {
        WanderingTraveler.init();

        // Before anything reads a setting.
        TravelerClothConfig.Plumbing.register();

        // NeoForge has its own Config button in the mods list, so nothing like Mod Menu
        // is needed here. The extension point is CLIENT ONLY, which is free for us -
        // this whole @Mod is dist = CLIENT.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> TravelerClothConfig.Plumbing.screen(parent));

        // AddClientReloadListenersEvent and RegisterGuiLayersEvent are MOD bus events -
        // both implement IModBusEvent - while ClientTickEvent is a GAME bus event.
        // Registering either on the wrong bus fails silently at runtime.
        modBus.addListener(WanderingTravelerNeoForge::onAddReloadListeners);
        modBus.addListener(WanderingTravelerNeoForge::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(WanderingTravelerNeoForge::onClientTick);
    }

    private static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        // The title images have to be DISCOVERED rather than listed: a missing texture
        // draws as the magenta box, so the mod must know what exists before it asks.
        event.addListener(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "titles"),
                (ResourceManagerReloadListener) TitleArt::reload);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // Below the vanilla title layer, so a /title from a datapack or another mod
        // still draws over ours rather than being hidden by it.
        event.registerBelow(VanillaGuiLayers.TITLE,
                Identifier.fromNamespaceAndPath(Constants.MOD_ID, "biome_title"),
                TitleRenderer::render);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        TitleTracker.tick(Minecraft.getInstance());
    }
}
