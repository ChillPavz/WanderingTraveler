package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.TravelerClothConfig;
import com.chillpavz.wanderingtraveler.title.TitleArt;
import com.chillpavz.wanderingtraveler.title.TitleRenderer;
import com.chillpavz.wanderingtraveler.title.TitleTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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

        // The title glyphs have to be DISCOVERED rather than listed: an unknown font id
        // is a silent no-op in vanilla, so the mod must know what exists before it asks.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new TitleReloadListener());

        ClientTickEvents.END_CLIENT_TICK.register(TitleTracker::tick);

        // HudRenderCallback, NOT the layered API. Measured across every version this jar
        // covers: HudRenderCallback is the only HUD hook present on all of 1.21 through
        // 1.21.5. HudLayerRegistrationCallback and IdentifiedLayer, which the newer
        // branches use to attach BEFORE the vanilla title, do not arrive until 1.21.4.
        //
        // The cost is ordering only. This draws after the whole vanilla HUD, so a title
        // sent by a datapack or another mod no longer wins over ours; on 1.21.6 and above
        // it does. Splitting the band at 1.21.4, or reflecting for the two versions that
        // have the layered API, would both be a lot of machinery for a case that needs a
        // vanilla title and a biome crossing in the same moment.
        HudRenderCallback.EVENT.register(TitleRenderer::render);
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
