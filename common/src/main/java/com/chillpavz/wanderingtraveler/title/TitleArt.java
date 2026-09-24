package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.Constants;
import com.chillpavz.wanderingtraveler.config.TravelerConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds the hand-made titles and turns a biome or dimension id into something drawable.
 *
 * <h2>Why the art is a font glyph</h2>
 * A "3D title" is a bitmap font glyph: a PNG declared in a font definition, mapped to a
 * private-use codepoint, and drawn as ordinary text. The {@code bitmap} provider format
 * is byte-identical from 1.20.1 to 26.x, so the artwork ports across the whole version
 * range for free and only a handful of Java calls differ per version.
 *
 * <h2>Why the font ids are DISCOVERED and not listed</h2>
 * {@code FontManager.getFontSetRaw} is {@code fontSets.getOrDefault(id, missingFontSet)}
 * - an unknown font id throws nothing and logs nothing, it just draws missing-glyph
 * boxes. So the mod has to know what exists before it asks, and the only honest way to
 * know is to list the resources on every reload.
 *
 * <p>Listing also buys the extensibility the whole design rests on: a resource pack adds
 * a title for any modded or datapack biome by shipping two files under our namespace,
 * with no code and no permission from us. The incumbent's companion pack is All Rights
 * Reserved and its author openly cannot keep up with the requests, which is the hole
 * this fills.
 */
public final class TitleArt {

    /**
     * A title is several glyphs, not one.
     *
     * <p>One glyph may not exceed the 256 px font sheet, so a whole ~2048 px render
     * squeezed into a single glyph is thrown away and then magnified back up by the
     * game - which is why the first builds looked like an enlarged small photo. The
     * generator slices each title into vertical strips instead, strip i taking the
     * codepoint {@code FIRST_GLYPH + i}.
     *
     * <p>{@code SPACER} closes the seams. Vanilla adds one unit to every glyph's
     * advance ({@code round(actualGlyphWidth * scale) + 1}), so the font declares a
     * space provider with an advance of -1 and it goes between every pair of strips.
     */
    private static final char FIRST_GLYPH = '';
    private static final char SPACER = '';

    private static final String FONT_PREFIX = "font/";
    private static final String FONT_DIR = FONT_PREFIX + "title";

    /** biome/dimension id -> the title's font, declared height and strip count. */
    private static Map<Identifier, Entry> available = Map.of();

    public record Entry(Identifier font, int height, int strips) {
    }

    private TitleArt() {
    }

    /**
     * Rebuild the table of available titles. Call on every resource reload.
     *
     * <p>The height is parsed out of each font definition rather than measured, because
     * the renderer needs it to place the glyph: a bitmap glyph sits {@code ascent} units
     * above the text baseline, so a 66-unit-tall title drawn at a naive y would hang off
     * the top of its own box.
     */
    public static void reload(ResourceManager resources) {
        // DEGRADE, NEVER CRASH. This runs inside the game's resource reload, and an
        // exception escaping it fails the whole reload rather than just this mod. With
        // nothing discovered every title still shows, as plain text.
        try {
            discover(resources);
        } catch (Throwable t) {
            available = Map.of();
            Constants.LOG.error("Could not discover title glyphs; titles will show as "
                    + "plain text", t);
        }
    }

    /**
     * Every title font definition under our namespace, each resolved to the pack that
     * wins it.
     *
     * <p><b>NOT {@code ResourceManager.listResources}, which cannot span 26.3.</b> Its
     * second parameter is a {@code Predicate<Identifier>} up to 26.2 and a
     * {@code ResourceManager.Selector} from 26.3, so any call compiled against one
     * version throws {@code NoSuchMethodError} on the other - and here that is inside
     * the resource reload. {@code listPacks}, {@code PackResources.listResources} and
     * {@code ResourceProvider.getResource} have identical descriptors on 26.1 through
     * 26.3, read from the client jars, so they are what this is built from.
     *
     * <p>Asking {@code getResource} for each id found gives the same answer the merged
     * listing gave: the resource from the highest-priority pack that has that path, so
     * a resource pack overriding one of our titles still wins.
     */
    private static void discover(ResourceManager resources) {
        Set<Identifier> paths = new TreeSet<>();
        resources.listPacks().forEach((PackResources pack) -> pack.listResources(
                PackType.CLIENT_RESOURCES, Constants.MOD_ID, FONT_DIR,
                (id, supplier) -> {
                    if (id.getPath().endsWith(".json")) {
                        paths.add(id);
                    }
                }));

        Map<Identifier, Entry> found = new HashMap<>();
        for (Identifier path : paths) {
            Optional<Resource> winner = resources.getResource(path);
            if (winner.isEmpty()) {
                continue;
            }
            // wanderingtraveler:font/title/<ns>/<name>.json  ->  <ns>:<name>
            String rel = path.getPath().substring(FONT_DIR.length() + 1);
            rel = rel.substring(0, rel.length() - ".json".length());
            int slash = rel.indexOf('/');
            if (slash <= 0 || slash == rel.length() - 1) {
                Constants.LOG.warn("Ignoring title font {}: expected title/<namespace>/<name>.json",
                        path);
                continue;
            }
            Identifier subject = Identifier.fromNamespaceAndPath(
                    rel.substring(0, slash), rel.substring(slash + 1));
            // A FONT ID IS THE RESOURCE PATH WITHOUT ITS "font/" PREFIX AND ".json"
            // SUFFIX - the game resolves ns:path back to assets/ns/font/path.json. The
            // first version built the id straight from FONT_DIR and so carried a second
            // "font/", which resolves to nothing; and an unknown font id throws nothing
            // and logs nothing, it just draws missing-glyph boxes. Derived here by
            // stripping, which is the definition, so it cannot drift again.
            String fontPath = path.getPath();
            fontPath = fontPath.substring(FONT_PREFIX.length(),
                    fontPath.length() - ".json".length());
            Identifier font = Identifier.fromNamespaceAndPath(Constants.MOD_ID, fontPath);

            Entry entry = read(winner.get(), path, font);
            if (entry != null) {
                found.put(subject, entry);
            }
        }
        available = Map.copyOf(found);
        Constants.LOG.info("Found {} title glyphs", available.size());
    }

    /**
     * Read the declared height and count the strips.
     *
     * <p>The strip count is the number of BITMAP providers - the space provider that
     * closes the seams does not draw anything - so the mod never has to be told how a
     * title was sliced. A third-party pack that ships a one-strip title works with no
     * change at all.
     */
    private static Entry read(Resource resource, Identifier path, Identifier font) {
        // DEGRADE, NEVER CRASH: a malformed font file from a third-party pack must cost
        // that one title and nothing else.
        try (BufferedReader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int height = 0;
            int strips = 0;
            for (JsonElement provider : root.getAsJsonArray("providers")) {
                JsonObject obj = provider.getAsJsonObject();
                if (!"bitmap".equals(obj.has("type") ? obj.get("type").getAsString() : "")) {
                    continue;
                }
                strips++;
                if (height == 0 && obj.has("height")) {
                    height = obj.get("height").getAsInt();
                }
            }
            if (height > 0 && strips > 0) {
                return new Entry(font, height, strips);
            }
            Constants.LOG.warn("Title font {} has no usable bitmap provider", path);
        } catch (Exception e) {
            Constants.LOG.warn("Could not read title font {}: {}", path, e.toString());
        }
        return null;
    }

    /** The artwork for this id, or null if there is none. */
    public static Entry artworkFor(Identifier subject) {
        return TravelerConfig.useArtwork ? available.get(subject) : null;
    }

    /**
     * The drawable component for a title.
     *
     * <p>A FULL-COLOUR GLYPH IS STILL MULTIPLIED BY THE TEXT COLOUR, so the artwork is
     * forced to white here and the colour option applies to the plain-text path only.
     * Tinting a coloured glyph any other colour simply darkens it, and that is invisible
     * in a screenshot taken at the default.
     */
    public static Component artworkComponent(Entry entry) {
        StringBuilder glyphs = new StringBuilder();
        for (int i = 0; i < entry.strips(); i++) {
            if (i > 0) {
                glyphs.append(SPACER);
            }
            glyphs.append((char) (FIRST_GLYPH + i));
        }
        MutableComponent text = Component.literal(glyphs.toString());
        return text.withStyle(style -> style
                .withFont(new FontDescription.Resource(entry.font()))
                .withColor(0xFFFFFF));
    }

    /**
     * The plain-text fallback.
     *
     * <p>NEVER A RAW KEY AND NEVER "???". Both incumbents fail here - TravelerZ prints
     * {@code terralith.biomename.whatever} with Terralith installed, and the other shows
     * "???" for runtime-created dimensions - and users call it immersion-breaking. The
     * translation key is tried first so every modded and datapack biome works untouched,
     * and a humanised id is the last resort.
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
