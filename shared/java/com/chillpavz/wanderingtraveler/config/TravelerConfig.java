package com.chillpavz.wanderingtraveler.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Plain holder for every option, living in common.
 *
 * <p>It is deliberately not a Cloth Config {@code @Config} class: common cannot see a
 * loader dependency, so the annotated class is duplicated per loader module and pushes
 * its values in here. The two annotated copies are byte-identical on purpose, and
 * {@code tools/audit_jar.py} asserts that they stay so.
 *
 * <p>The names are chosen to answer a real complaint. The incumbent's repeat-suppression
 * setting is called "Number of Most Recent Biomes Saved", and three separate people
 * opened issues because they could not find it or work out that 0 means "always show".
 * A correct feature with an unguessable name generated three support tickets, so these
 * are named after what the player wants rather than after the data structure.
 *
 * <h2>Why the scales are integer percentages</h2>
 * Cloth's {@code @ConfigEntry.BoundedDiscrete} only makes a SLIDER for an integral
 * field - its {@code min} and {@code max} are longs - so a float scale gets a text box
 * instead. The owner asked for sliders, so the stored unit is a percentage and the
 * renderer divides. It also makes the config file readable.
 */
public final class TravelerConfig {

    /** Master switch. */
    public static boolean enabled = true;

    /** Use the hand-made artwork where a title exists, rather than plain text. */
    public static boolean useArtwork = true;

    /**
     * Show the explanatory tooltips in the config screen itself.
     *
     * <p>OFF by default (owner, Aug 2026). A tooltip is drawn at the cursor, and on a
     * slider that is exactly over the value you are dragging to, so the screen is easier
     * to use without them. The option names carry the meaning; the tooltips are there for
     * anyone who wants the detail.
     */
    public static boolean showTooltips = false;

    /**
     * Dimension titles are OFF by default (owner, Aug 2026).
     *
     * <p>ON since all three dimensions have artwork (owner, Aug 2026). It stayed off
     * while the Nether had none, because turning it on would have mixed hero art with
     * plain text on the same screen.
     *
     * <p>A dimension change announces the dimension INSTEAD of the biome it drops you
     * into, not as well as it. Overlapping the two is what the incumbent does and it is
     * the subject of both an open issue and a store-page question; queueing both would
     * mean two titles on every single portal, which is worse than either.
     */
    public static boolean showDimensionTitles = true;

    // ------------------------------------------------------------------ placement
    public static Anchor anchor = Anchor.TOP_CENTER;
    /**
     * A pure nudge in GUI-scaled pixels, applied after the anchor, and zero by default.
     *
     * <p>{@link Anchor} already lands each of its nine positions clear of the HUD, so
     * these exist to taste rather than to make the default readable. That is a change:
     * offsetY used to default to 30 because every anchor sat flush against its edge,
     * which put the middle row below the crosshair and the bottom row behind the hotbar.
     */
    public static int offsetX = 0;
    public static int offsetY = 0;

    // ----------------------------------------------------------------------- size
    /** The height every title image is drawn at, in GUI units, before these scales. */
    public static final int ARTWORK_UNITS = com.chillpavz.wanderingtraveler.title.TitleImages.HEIGHT_UNITS;
    /** Drawn width, in font units, at or below which a title counts as short. */
    public static final int SHORT_MAX_UNITS = 85;
    /** Drawn width, in font units, above which a title counts as long. */
    public static final int MEDIUM_MAX_UNITS = 120;

    /**
     * A title's on-screen size as a percentage, in three bands by NAME LENGTH.
     *
     * <p>Every title is drawn {@link #ARTWORK_UNITS} GUI units tall, so
     * these are the only thing deciding how big it lands: 300 is 36 GUI-scaled pixels.
     * Three bands rather than one because a long name at the scale that suits a short
     * one runs into the screen edge, and being cut off - or silently shrunk by the
     * fallback below - is worse than being drawn a little smaller on purpose.
     *
     * <p>The bands are measured in font units of drawn width, which is the number the
     * renderer already has. Across the shipped titles (66 when this was measured) that puts 27 in short (River
     * at 37 units up to Warm Ocean at 85), 28 in medium, and 11 in long (Windswept
     * Forest at 121 up to Windswept Gravelly Hills at 172).
     */
    public static int artworkScaleShort = 300;
    public static int artworkScaleMedium = 275;
    public static int artworkScaleLong = 250;

    /**
     * Plain text as a percentage, for biomes with no artwork. 300 is a 27 pixel line.
     *
     * <p>This started at 400 to match the artwork's 300, on the reasoning that 12 units
     * at 3x and 9 units at 4x are both 36 pixels tall. The box heights are why that is
     * the wrong comparison: 12 units of artwork is a whole letter INCLUDING its extruded
     * side, its underside and its drop shadow, where 9 units of text is the line box of
     * a dense pixel font whose capitals fill most of it.
     *
     * <p>Halved again to 150 after the owner saw 300 in game (Aug 2026). Plain text is
     * the fallback for a biome with no artwork, so it should read as a caption rather
     * than compete with the hand-made titles.
     */
    public static int textScalePercent = 150;

    /** White text, and the shadow vanilla itself would draw behind white text. */
    public static final int DEFAULT_TEXT_COLOR = 0xFFFFFF;
    public static final int DEFAULT_SHADOW_COLOR = 0x3F3F3F;

    /**
     * The colour of a plain-text title, as 0xRRGGBB.
     *
     * <p>Plain text only. The artwork is MULTIPLIED by any colour it is drawn with, so
     * tinting it any other colour simply darkens it - which is invisible in a
     * screenshot taken at the default and looks like a broken palette to anyone who
     * changes it. The artwork is forced to white in the renderer for that reason.
     */
    public static int textColor = DEFAULT_TEXT_COLOR;

    /**
     * Work the shadow colour out from the text colour instead of using the one below.
     *
     * <p>On by default. The first version had no toggle: the shadow colour field WAS the
     * switch, deriving whenever it sat at its default and being used otherwise. That is
     * two behaviours in one control and the owner rightly called it unintuitive - nothing
     * on screen told you which of the two you were looking at.
     */
    public static boolean deriveShadowColor = true;

    /** The shadow colour to use when {@link #deriveShadowColor} is off. */
    public static int shadowColor = DEFAULT_SHADOW_COLOR;

    /**
     * A darker colour that suits this one, for use as its shadow.
     *
     * <p>Vanilla's own rule. Minecraft shades every other piece of text with
     * {@code ARGB.scaleRGB(colour, 0.25f)}, which truncates, and
     * {@code (colour & 0xFCFCFC) >> 2} is that in integer arithmetic - equal for every
     * channel value, checked rather than assumed. Scaling all three channels by the same
     * factor leaves hue and saturation untouched and moves only brightness, so the result
     * is always the same colour in shadow, and it matches what the rest of the game does
     * so a title never looks foreign. White gives {@link #DEFAULT_SHADOW_COLOR}.
     */
    public static int deriveShadow(int colour) {
        return (colour & 0xFCFCFC) >> 2;
    }

    /** The shadow colour to actually draw with. */
    public static int shadowColorToDraw() {
        return deriveShadowColor ? deriveShadow(textColor) : shadowColor;
    }

    /** Vanilla's drop shadow behind a plain-text title. Never behind the artwork. */
    public static boolean textShadow = true;

    /** The multiplier to draw an artwork title of this drawn width at. */
    public static float artworkScaleFor(int widthUnits) {
        int percent = widthUnits <= SHORT_MAX_UNITS ? artworkScaleShort
                : widthUnits <= MEDIUM_MAX_UNITS ? artworkScaleMedium
                : artworkScaleLong;
        return percent / 100.0F;
    }

    public static float textScale() {
        return textScalePercent / 100.0F;
    }

    // --------------------------------------------------------------------- timing
    public static int fadeInTicks = 8;
    public static int holdTicks = 40;
    public static int fadeOutTicks = 16;

    /**
     * Ticks a title is GUARANTEED at full opacity before the next one may end its hold.
     *
     * <p>This replaced a minimum GAP between titles, and the difference is the whole of
     * the fix. A gap made a new title wait, and when it finally arrived it replaced the
     * one in front of it, so the outgoing fade out never happened and the incoming title
     * appeared up to two and a half seconds after the border was crossed. Nothing waits
     * now: crossing a border ends the current title's hold.
     *
     * <p>What this number buys is the one thing that has to be defended when a player is
     * moving fast: that each title is actually READ. Below it nothing may interrupt, so
     * a run of borders cannot turn into a strobe of half drawn names. It is also, with
     * the two fades, the fastest titles can follow each other: 8 + 20 + 16 ticks is one
     * title per 2.2 seconds at most, close to the old gap's calm without its wait.
     *
     * <p>20 since 1.2.0 (owner, Oct 2026: a full second at full opacity), was 10. Capped at
     * {@link #holdTicks} in use, so it can never make a title outlive its own configured
     * hold. 0 lets every arrival end the hold at once.
     */
    public static int minTimeOnScreenTicks = 20;

    /**
     * Shorten both fades when borders arrive faster than a whole title takes.
     *
     * <p>The 1.1.0 behaviour, kept as an option. Off by default (owner, Oct 2026): titles
     * kept up, but the shortened fades read as rushed, and a biome title behind a
     * dimension title faded in over three ticks.
     */
    public static boolean fasterFadesWhenBusy = false;

    /**
     * When the player arrives somewhere that will not be announced (it is the title just
     * shown, or it was shown too recently), cut the title of the place they have left.
     *
     * <p>Off by default (owner, Oct 2026): the title plays out. Cutting it was what 1.2.0's
     * quick returns did, and with a short repeat cooldown it brings back a run of cuts
     * along a border.
     */
    public static boolean endTitleWhenLeaving = false;

    // ----------------------------------------------------------------- situations
    /**
     * Elytra flight crosses biomes faster than titles can be read.
     *
     * <p>Off by default: the queue above already stops the spam, and a player who fitted
     * a mod about biome titles probably wants them while exploring, which is most of
     * what an elytra is for. It is here because some people will find it distracting.
     */
    public static boolean hideWhileGliding = false;

    /** What to do with a SURFACE biome's title while the player is underground. */
    public enum UndergroundTitles {
        /** Nothing changes. The default. */
        SHOW,
        /** Draw it as plain text: still readable, but it does not take over the screen. */
        PLAIN_TEXT,
        /** Do not show it at all. */
        HIDE;

        /** See {@link Anchor#toString()} - Cloth labels an enum with its toString. */
        @Override
        public String toString() {
            return "wanderingtraveler.underground." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * HIDE by default (owner, Aug 2026, after seeing it in game).
     *
     * <p>Announcing the surface biome you happen to be under while you are in a cave is
     * noise: it is not where you are, and it fires again every time a cave wanders across
     * a border overhead. Anyone who wants it can turn it back on.
     *
     * <p>Both halves of this are decided by looking rather than from a list. A biome
     * counts as a SURFACE biome when it also occurs at ground level in a grid of columns
     * around the player, so a cave biome is simply one that occurs nowhere up there. That
     * needs no cave-biome tag, which matters because THERE IS NO {@code minecraft:is_cave}
     * TAG - checked against the 1.20.1 and 26.2 client jars, whose only vanilla biome tags are
     * is_badlands, is_beach, is_deep_ocean, is_end, is_forest, is_hill, is_jungle,
     * is_mountain, is_nether, is_ocean, is_overworld, is_river, is_savanna and is_taiga -
     * and because a client-only mod cannot ship one anyway: biome tags arrive from the
     * server. Deciding it by looking works for every modded and datapack cave biome with
     * nothing shipped and nothing configured.
     */
    public static UndergroundTitles undergroundTitles = UndergroundTitles.HIDE;

    // ---------------------------------------------------------------- suppression
    public enum ShowTitleWhen {
        /** Every time you cross a border, however often that is. The default. */
        ALWAYS,
        /** Not if you have seen this title in the last few biomes. */
        NOT_SEEN_RECENTLY,
        /** Only the first time you ever see it this session. */
        FIRST_TIME_ONLY;

        /** See {@link Anchor#toString()} - Cloth labels an enum with its toString. */
        @Override
        public String toString() {
            return "wanderingtraveler.showwhen." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * ALWAYS by default (owner, Aug 2026).
     *
     * <p>Title spam is the loudest complaint on the incumbent, so suppression is built
     * and stays one config away - but its default is off, because a player who has just
     * installed a mod about biome titles should see biome titles. The three people who
     * opened issues on the incumbent were all trying to turn suppression OFF, not on.
     */
    public static ShowTitleWhen showTitleWhen = ShowTitleWhen.ALWAYS;

    /** How many recently shown titles to remember. Only used by NOT_SEEN_RECENTLY. */
    public static int rememberLastTitles = 5;

    /**
     * Ticks before the same title may be shown again, counted from when it last STARTED.
     *
     * <p>Replaces two 1.2.0 rules: Ignore Quick Returns (coming back into a biome within
     * five seconds announced nothing and cut the title on screen) and "never the same
     * title twice in a row", which had no time limit. Both suppressed real arrivals by a
     * rule the player could not see; this one is a plain promise that a name is not
     * repeated within the cooldown, whatever lies between. With Distance Into a Biome
     * already ignoring edges, it is what stops a walk along a river or a beach naming the
     * same two biomes back and forth.
     *
     * <p>Applies to border crossings and to climbing out of a cave into a biome that was
     * hidden underground. Not to landing from a glide, which always announces where the
     * player lands. 0 lets a name repeat as soon as its title has left the screen.
     *
     * <p>OFF by default (owner, Oct 2026, after play testing): a title that shows is better
     * than one that does not, and a missing title reads as a bug. Distance Into a Biome
     * already keeps a walk along a border calm without it. Measured for anyone tuning it:
     * zigzagging across a border showed 4 titles at 10 s, 3 at 15 s and 2 at 20 s, and 30 s
     * changed nothing more.
     */
    public static int sameTitleCooldownTicks = 0;

    /**
     * Blocks the player must be inside a new biome, in every direction, before it counts.
     *
     * <p>Walking along an edge is then not entering anything, so it announces nothing.
     * The cost is narrow biomes: a river or a beach narrower than twice this is never
     * announced by walking straight across it. 0 counts the moment the line is crossed.
     *
     * <p>3 blocks (owner, Oct 2026, from measured routes; was 4): a title is fully on
     * screen about 1.0 s after the border at walking pace against 1.3 s, for one more title
     * per half minute walking along a riverbank. 2 doubled the riverbank titles, 0 gave
     * twelve or more across the edge walks, and above 4 only made titles later.
     */
    public static int distanceIntoBiome = 3;

    /**
     * Do not announce the biome you spawn into.
     *
     * <p>"You didn't travel anywhere" - two people asked for this on the incumbent and
     * it has been open since 2023. It is OFF by default all the same (owner, Aug 2026):
     * someone who has just installed a mod about biome titles, loads a world and sees
     * nothing at all concludes it is broken, and that costs more than the immersion the
     * option buys back.
     */
    public static boolean hideOnWorldJoin = false;

    /** Biome or dimension ids that never show a title, e.g. "minecraft:river". */
    private static Set<String> neverShow = Set.of();

    public static boolean isSilenced(String id) {
        return neverShow.contains(id);
    }

    /**
     * Take the list the config screen edited and make it usable.
     *
     * <p>A player typing {@code river} into that list means {@code minecraft:river}, and
     * a stray space or a capital should not silently do nothing. Normalising here rather
     * than at the lookup means the same rule applies however the value arrived.
     */
    public static void setNeverShow(List<String> ids) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : ids) {
            String id = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            out.add(id.indexOf(':') < 0 ? "minecraft:" + id : id);
        }
        neverShow = Set.copyOf(out);
    }

    private TravelerConfig() {
    }
}
