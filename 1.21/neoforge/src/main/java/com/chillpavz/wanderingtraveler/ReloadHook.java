package com.chillpavz.wanderingtraveler;

import com.chillpavz.wanderingtraveler.title.TitleArt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.lang.reflect.Method;

/**
 * Registers the title discovery as a client reload listener, on whichever event this
 * NeoForge has.
 *
 * <h2>The event is not the same across the versions this jar serves</h2>
 * NeoForge 21.0 to 21.3 (1.21 to 1.21.3) have {@code RegisterClientReloadListenersEvent},
 * whose {@code registerReloadListener} takes the listener alone. NeoForge 21.4 and 21.5
 * (1.21.4 and 1.21.5) REMOVED it in favour of {@code AddClientReloadListenersEvent}, whose
 * {@code addListener(ResourceLocation, PreparableReloadListener)} is inherited from
 * {@code SortedReloadListenerEvent}. Both read out of the universal jars with javap. A jar
 * that names the old class fails to load on 21.4 with NoClassDefFoundError, which takes the
 * whole game down at mod loading.
 *
 * <p>So the old event lives in {@link Legacy}, a class the JVM only loads when that event
 * exists, and the new one is reached by reflection, because this module compiles against
 * 21.0, where it does not exist yet. NeoForge runs on Mojang names, so the names are stable.
 *
 * <p>DEGRADE, NEVER CRASH: if neither works, titles still show, as plain text, because
 * nothing was discovered.
 */
final class ReloadHook {

    private static final String SORTED_EVENT =
            "net.neoforged.neoforge.client.event.AddClientReloadListenersEvent";

    private ReloadHook() {
    }

    static void register(IEventBus modBus) {
        try {
            Class<?> sorted = find(SORTED_EVENT);
            if (sorted != null) {
                registerSorted(modBus, sorted.asSubclass(Event.class));
            } else {
                Legacy.register(modBus);
            }
        } catch (Throwable t) {
            Constants.LOG.error("Could not register the title discovery; titles will show as "
                    + "plain text", t);
        }
    }

    private static <T extends Event> void registerSorted(IEventBus modBus, Class<T> type)
            throws NoSuchMethodException {
        Method add = type.getMethod("addListener",
                ResourceLocation.class, PreparableReloadListener.class);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "titles");
        modBus.addListener(type, event -> {
            try {
                add.invoke(event, id, (ResourceManagerReloadListener) TitleArt::reload);
            } catch (ReflectiveOperationException e) {
                Constants.LOG.error("Could not register the title discovery; titles will "
                        + "show as plain text", e);
            }
        });
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, ReloadHook.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** NeoForge 21.0 to 21.3 only. Never loaded where the event does not exist. */
    private static final class Legacy {

        private Legacy() {
        }

        static void register(IEventBus modBus) {
            modBus.addListener(Legacy::onRegister);
        }

        private static void onRegister(RegisterClientReloadListenersEvent event) {
            // Takes NO id, unlike the sorted event that replaced it.
            event.registerReloadListener((ResourceManagerReloadListener) TitleArt::reload);
        }
    }
}
