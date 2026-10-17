package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.Constants;
import com.chillpavz.wanderingtraveler.config.TravelerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Finds the hand-made titles and turns a biome or dimension id into something drawable.
 *
 * <h2>One PNG per title, drawn as a GUI texture</h2>
 * {@code textures/title/<namespace>/<name>.png} under our namespace is the artwork for
 * {@code <namespace>:<name>} ({@link TitleImages}). The renderer draws it with a blit, so
 * there is no 256 px ceiling and nothing to slice.
 *
 * <h2>Why the titles are DISCOVERED and not listed</h2>
 * A texture that does not exist draws as the magenta missing texture, so the mod has to
 * know what exists before it asks, and the only honest way to know is to list the
 * resources on every reload. Listing also means a resource pack adds a title for any
 * modded or datapack biome by shipping one PNG under our namespace, with no code.
 */
public final class TitleArt {

    /** biome/dimension id -> the title's texture and pixel size. */
    private static Map<Identifier, Entry> available = Map.of();

    /** A title image: its texture and its size in pixels. */
    public record Entry(Identifier texture, int width, int height) {

        /** The width in GUI units when drawn {@link TitleImages#HEIGHT_UNITS} tall. */
        public int widthUnits() {
            return TitleImages.widthUnits(width, height);
        }
    }

    private TitleArt() {
    }

    /** Rebuild the table of available titles. Call on every resource reload. */
    public static void reload(ResourceManager resources) {
        // DEGRADE, NEVER CRASH. This runs inside the game's resource reload, and an
        // exception escaping it fails the whole reload rather than just this mod. With
        // nothing discovered every title still shows, as plain text.
        try {
            discover(resources);
        } catch (Throwable t) {
            available = Map.of();
            Constants.LOG.error("Could not discover title images; titles will show as "
                    + "plain text", t);
        }
    }

    /**
     * Every title image under our namespace. {@code listResources} already resolves each
     * path to the pack that wins it, so a resource pack overriding a title wins here too.
     */
    private static void discover(ResourceManager resources) {
        Map<Identifier, Resource> files = resources.listResources(
                TitleImages.DIR, id -> id.getPath().endsWith(".png"));

        Map<Identifier, Entry> found = new HashMap<>();
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            Identifier path = file.getKey();
            if (!Constants.MOD_ID.equals(path.getNamespace())) {
                continue;
            }
            String[] subject = TitleImages.subjectOf(path.getPath());
            if (subject == null) {
                Constants.LOG.warn("Ignoring {}: expected {}/<namespace>/<name>.png",
                        path, TitleImages.DIR);
                continue;
            }
            try (InputStream in = file.getValue().open()) {
                int[] size = TitleImages.size(in);
                Identifier id = Identifier.fromNamespaceAndPath(subject[0], subject[1]);
                found.put(id, new Entry(path, size[0], size[1]));
            } catch (Exception e) {
                Constants.LOG.warn("Could not read title image {}: {}", path, e.toString());
            }
        }
        available = Map.copyOf(found);
        Constants.LOG.info("Found {} title images", available.size());
    }

    /** The artwork for this id, or null if there is none. */
    public static Entry artworkFor(Identifier subject) {
        return TravelerConfig.useArtwork ? available.get(subject) : null;
    }

    /**
     * The plain-text title. The lang key is the vanilla one, so every modded biome that
     * ships a proper name gets it for free; the fallback is the id made readable.
     */
    public static Component plainText(String translationKey, Identifier subject) {
        return Component.translatableWithFallback(translationKey, humanise(subject));
    }

    /** {@code minecraft:old_growth_pine_taiga} -> {@code Old Growth Pine Taiga}. */
    public static String humanise(Identifier subject) {
        StringBuilder out = new StringBuilder();
        for (String word : subject.getPath().split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.length() == 0 ? subject.toString() : out.toString();
    }
}
