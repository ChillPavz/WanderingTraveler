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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint, for 1.21.1 only.
 *
 * <h2>Why 1.21.1 and not 1.21</h2>
 * Forge 51, the whole of 1.21, ships NO HUD overlay API: no AddGuiOverlayLayersEvent, no
 * client.gui.overlay package, and nothing equivalent among its client events. A HUD layer
 * cannot be added there without a mixin, and this mod has none by design. The API appears
 * at Forge 52.1.2 exactly, which is why the accepted range has a real floor.
 *
 * <h2>Forge 52 is EventBus 6, so this looks nothing like the 1.21.11 module</h2>
 * There are no static {@code BUS} fields here. The mod bus comes from
 * {@link FMLJavaModLoadingContext} and the game bus is {@link MinecraftForge#EVENT_BUS},
 * which is the pre-rewrite shape; the 1.21.11 branch uses BusGroup and static BUS fields
 * instead. Both HUD and reload events implement {@code IModBusEvent} and so go on the MOD
 * bus, while the tick event is a game-bus event with a {@code phase} field rather than
 * nested Pre and Post types. Getting a bus wrong fails SILENTLY at runtime.
 */
@Mod(Constants.MOD_ID)
public class WanderingTravelerForge {

    public WanderingTravelerForge() {
        WanderingTraveler.init();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(WanderingTravelerForge::onRegisterReloadListeners);
        modBus.addListener(WanderingTravelerForge::onAddGuiLayers);

        MinecraftForge.EVENT_BUS.addListener(WanderingTravelerForge::onClientTick);

        // Client only. The config screen classes must never be loaded on a dedicated
        // server, so the reference sits behind this guard in a separate class.
        // FMLEnvironment.dist is a FIELD on classic Forge.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            WanderingTravelerConfigScreenRegistrar.register();
        }
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // The title glyphs have to be DISCOVERED rather than listed: an unknown font id is
        // a silent no-op in vanilla, so the mod must know what exists before it asks.
        event.registerReloadListener((ResourceManagerReloadListener) TitleArt::reload);
    }

    private static void onAddGuiLayers(AddGuiOverlayLayersEvent event) {
        // THE VANILLA HUD IS A TREE AND minecraft:title IS NOT IN ITS ROOT: it lives in the
        // post_sleep_phase sub-stack. The three argument addBelow uses the receiver's own
        // name as the stack, so it would add the layer to the root and then fail to order
        // it against a target one level down - silently, with nothing drawn at all. The
        // four argument form names the stack.
        //
        // Order is (stack, newId, target, layer), read out of the bytecode rather than
        // assumed: the lambda warns when the TARGET name is still available, then does
        // add(newId, layer) followed by move(newId, target, BELOW). Below the title, so a
        // /title from a datapack or another mod still draws over ours.
        event.getLayeredDraw().addBelow(
                ForgeLayeredDraw.POST_SLEEP_STACK,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "biome_title"),
                ForgeLayeredDraw.TITLE_OVERLAY,
                TitleRenderer::render);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        // One tick per game tick: this event fires twice, once per phase.
        if (event.phase == TickEvent.Phase.END) {
            TitleTracker.tick(Minecraft.getInstance());
        }
    }
}
