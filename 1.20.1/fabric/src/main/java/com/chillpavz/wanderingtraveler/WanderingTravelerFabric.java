package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.config.ClothCompat;
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

        // Before anything reads a setting, and only if cloth is actually installed - it
        // is OPTIONAL on this band. See ClothCompat for why.
        ClothCompat.register();

        // The title images have to be DISCOVERED rather than listed: a missing texture
        // draws as the magenta box, so the mod must know what exists before it asks.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new TitleReloadListener());

        ClientTickEvents.END_CLIENT_TICK.register(TitleTracker::tick);

        // HudRenderCallback, and here it is the ONLY option rather than a choice between
        // two. It takes (GuiGraphics, float) on this band and (GuiGraphics, DeltaTracker)
        // from 1.21, which is the wall this jar stops below; the layered APIs the newer
        // branches use do not arrive until 1.21.4 and 1.21.6. Checked against both
        // fabric-api builds a player could install here, 0.83.0+1.20 and 0.92.x+1.20.1:
        // the signature is identical in both.
        //
        // The cost is ordering only. This draws after the whole vanilla HUD, so a title
        // sent by a datapack or another mod no longer wins over ours.
        //
        // It does NOT fire while F1 is held: GameRenderer only calls Gui.render when
        // hideGui is false or a screen is open, so there is nothing to test here even
        // though Options.hideGui does exist on this version.
        HudRenderCallback.EVENT.register(TitleRenderer::render);
    }

    // BOTH interfaces are needed: IdentifiableResourceReloadListener only adds the
    // id, and its supertype still demands the four-argument async reload.
    // ResourceManagerReloadListener is what supplies the simple synchronous default.
    private static final class TitleReloadListener
            implements IdentifiableResourceReloadListener, ResourceManagerReloadListener {

        // new ResourceLocation(ns, path): the fromNamespaceAndPath factory the newer
        // branches call does not exist until 1.21.
        private static final ResourceLocation ID =
                new ResourceLocation(Constants.MOD_ID, "titles");

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
