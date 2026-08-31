package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.config.TravelerConfig;
import net.minecraft.network.chat.Component;

/**
 * The one title currently on screen, the one waiting behind it, and how far through its
 * life the current one is.
 *
 * <p>Ticked from the client tick hook and read from the HUD hook, so the two never have
 * to agree about timing beyond a tick counter and a partial tick.
 *
 * <h2>Why there is a queue of exactly one</h2>
 * Crossing several borders quickly used to stamp each title over the one before it, so
 * none of them could be read; on an elytra that is a handful of biomes a second. A new
 * title now waits until {@link TravelerConfig#minTicksBetweenTitles} ticks after the last
 * one STARTED.
 *
 * <p>The queue holds ONE title and a newer arrival REPLACES it rather than joining a line
 * behind it. That is the whole of "show the first and the last and skip the ones in
 * between": a run of five biomes shows the first, then the fifth. A real queue would
 * instead play all five back to back, several seconds after the player left them, which
 * is the spam it exists to prevent wearing a different hat.
 *
 * <h2>Why the clock is separate from the title</h2>
 * {@link #sinceLastStart} keeps counting after a title has faded out, and that is
 * load-bearing rather than tidy. The first version counted only while a title was on
 * screen, so setting the minimum gap LONGER than a title's own life (which the slider
 * happily allows: 200 against a default life of 74) made the waiting title be thrown away
 * when the one in front of it expired, and it never appeared at all.
 */
public final class TitleState {

    /** {@link #sinceLastStart} when nothing has been shown, so the next title is instant. */
    private static final int LONG_AGO = Integer.MAX_VALUE / 2;

    /**
     * The beat between a dimension title and the biome title behind it, in ticks.
     *
     * <p>A dimension title is not competing with the biome title, it is introducing it,
     * so the biome waits for the dimension to play in FULL - fade out included - and then
     * for this pause on top. Reusing the ordinary minimum gap would start the biome title
     * while the dimension was still fading, which reads as rushed. Half a second is
     * enough of a beat to feel deliberate without becoming an empty screen.
     */
    private static final int AFTER_DIMENSION_PAUSE = 10;

    private static Component text;
    private static TitleArt.Entry artwork;
    private static int age;
    private static int lifetime;

    /** Ticks since ANY title last started, whether or not it is still on screen. */
    private static int sinceLastStart = LONG_AGO;

    /**
     * The DISPLAYED NAME of the title that last started, kept after it has faded out.
     *
     * <p>Used to cancel a title that would follow itself: crossing four borders quickly
     * and ending up back where you began would otherwise queue the first name behind
     * itself and play it twice.
     */
    private static String lastKey;
    private static boolean lastWasDimension;

    private static String pendingKey;
    private static Component pendingText;
    private static TitleArt.Entry pendingArtwork;
    private static boolean pendingIsDimension;

    private TitleState() {
    }

    /**
     * Put a title on screen, or behind the one in front of it.
     *
     * <p>Nothing here decides WHETHER a title is worth showing - that is the tracker's
     * job and it has already been done by the time this is called. What this does decide
     * is WHEN, and whether the title is worth showing AGAIN so soon.
     *
     * @param key the displayed name, which is what "the same title" means to a player
     */
    public static void show(String key, Component component, TitleArt.Entry entry,
                            boolean dimension) {
        int gap = gapAfterCurrent();
        if (sinceLastStart < gap && key.equals(lastKey)) {
            // BACK WHERE WE STARTED, so there is nothing to announce. Cross from Plains
            // into Forest and straight back, and the queue would otherwise replay Plains
            // over the Plains title already on screen: the same name fading into the
            // same name, which reads as a glitch rather than as a border crossing.
            // Dropping the pending title is the point - the trip cancels itself out.
            clearPending();
            return;
        }
        if (sinceLastStart >= gap) {
            begin(key, component, entry, dimension);
            return;
        }
        pendingKey = key;
        pendingText = component;
        pendingArtwork = entry;
        pendingIsDimension = dimension;
    }

    /** How long the title that last started keeps the screen to itself. */
    private static int gapAfterCurrent() {
        return lastWasDimension
                ? Math.max(lifetime + AFTER_DIMENSION_PAUSE, TravelerConfig.minTicksBetweenTitles)
                : TravelerConfig.minTicksBetweenTitles;
    }

    private static void begin(String key, Component component, TitleArt.Entry entry,
                              boolean dimension) {
        // CARRY THE OUTGOING OPACITY INTO THE INCOMING TITLE.
        //
        // A waiting title takes over after minTicksBetweenTitles, which is SHORTER than a
        // title's own life at the defaults: 50 against 8 + 40 + 16 = 64. Hold ends at 48,
        // so the title being replaced is typically two ticks into its sixteen tick fade
        // out and still around 87% opaque. Resetting age to 0 dropped that to nothing in
        // one frame and faded up again, which on screen reads as the title restarting, or
        // loading twice - reported on all three loaders, because this is shared code.
        //
        // Starting the new title at the age that REPRODUCES the old one's opacity makes
        // the swap continuous instead: alpha carries on from where it was rather than
        // snapping to zero. A title replaced while still fully opaque simply changes its
        // words, which is what a fast run of biomes should look like.
        float carried = alpha(0.0F);

        text = component;
        artwork = entry;
        sinceLastStart = 0;
        lastKey = key;
        lastWasDimension = dimension;
        lifetime = Math.max(1, TravelerConfig.fadeInTicks
                + TravelerConfig.holdTicks + TravelerConfig.fadeOutTicks);
        age = carried <= 0.0F || TravelerConfig.fadeInTicks <= 0
                ? 0
                : Math.min(TravelerConfig.fadeInTicks,
                        Math.round(TravelerConfig.fadeInTicks * carried));
        clearPending();
    }

    private static void clearPending() {
        pendingKey = null;
        pendingText = null;
        pendingArtwork = null;
        pendingIsDimension = false;
    }

    /** Forget everything, including the clock. Leaving a world, or a render failure. */
    public static void clear() {
        text = null;
        artwork = null;
        age = 0;
        lifetime = 0;
        sinceLastStart = LONG_AGO;
        lastKey = null;
        lastWasDimension = false;
        clearPending();
    }

    public static void tick() {
        if (sinceLastStart < LONG_AGO) {
            sinceLastStart++;
        }
        if (text != null && ++age >= lifetime) {
            text = null;
            artwork = null;
        }
        if (pendingText != null && sinceLastStart >= gapAfterCurrent()) {
            // A waiting title cuts the current one short rather than waiting out its full
            // fade, which is what keeps a fast run of biomes moving. The exception is a
            // dimension title, whose gap is its whole life plus a beat, so it is never
            // cut off by the biome it is introducing.
            begin(pendingKey, pendingText, pendingArtwork, pendingIsDimension);
        }
    }

    public static boolean isShowing() {
        return text != null;
    }

    public static Component text() {
        return text;
    }

    /** The artwork entry, or null when this title is plain text. */
    public static TitleArt.Entry artwork() {
        return artwork;
    }

    /**
     * Opacity 0..1 for this frame.
     *
     * <p>Uses the partial tick so the fade is smooth at any frame rate rather than
     * stepping once per tick.
     */
    public static float alpha(float partialTick) {
        if (text == null) {
            return 0.0F;
        }
        float now = age + partialTick;
        float fadeIn = TravelerConfig.fadeInTicks;
        float fadeOut = TravelerConfig.fadeOutTicks;
        float holdEnd = fadeIn + TravelerConfig.holdTicks;

        if (fadeIn > 0.0F && now < fadeIn) {
            return clamp(now / fadeIn);
        }
        if (fadeOut > 0.0F && now > holdEnd) {
            return clamp(1.0F - (now - holdEnd) / fadeOut);
        }
        return 1.0F;
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }
}
