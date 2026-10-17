package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.TravelerClothConfig;
import com.chillpavz.wanderingtraveler.title.TitleArt;
import com.chillpavz.wanderingtraveler.title.TitleRenderer;
import com.chillpavz.wanderingtraveler.title.TitleTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class WanderingTravelerFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WanderingTraveler.init();

        // Before anything reads a setting. The Config button itself comes from Mod
        // Menu, which is optional; the file and its values do not depend on it.
        TravelerClothConfig.Plumbing.register();

        // The title images have to be DISCOVERED rather than listed: a missing texture
        // draws as the magenta box, so the mod must know what exists before it asks.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new TitleReloadListener());

        ClientTickEvents.END_CLIENT_TICK.register(TitleTracker::tick);

        // Attached BEFORE the vanilla title element, so a /title from a datapack or
        // another mod still draws over ours rather than being hidden by it.
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.TITLE_AND_SUBTITLE,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "biome_title"),
                TitleRenderer::render);
    }

    // BOTH interfaces are needed: IdentifiableResourceReloadListener only adds the
    // id, and its supertype still demands the four-argument async reload.
    // ResourceManagerReloadListener is what supplies the simple synchronous default.
    private static final class TitleReloadListener
            implements IdentifiableResourceReloadListener, ResourceManagerReloadListener {

        private static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "titles");

        @Override
        public ResourceLocation getFabricId() {
            return ID;
        }

        @Override
        public void onResourceManagerReload(ResourceManager resources) {
            TitleArt.reload(resources);
        }
    }
}
