package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.config.TravelerConfig;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * The one title currently on screen, the one waiting behind it, and how opaque the
 * current one is this frame.
 *
 * <p>Ticked from the client tick hook and read from the HUD hook, so the two never have
 * to agree about timing beyond an opacity and a partial tick.
 *
 * <h2>Opacity is carried, not recomputed</h2>
 * The first version stored an AGE and worked opacity out from it every frame. That made
 * a handover impossible to do well: a title replaced mid-life had to have an age
 * invented for it that reproduced the outgoing opacity, and a title cut short simply
 * disappeared, because the fade out lived at a fixed position in someone else's
 * timeline.
 *
 * <p>Opacity is now the state, and a phase only says which direction it is moving and
 * how fast. Every transition is therefore continuous by construction: a fade can be
 * reversed, slowed or sped up on any tick and nothing on screen jumps.
 *
 * <h2>The three guarantees</h2>
 * Every title that starts, without exception:
 * <ol>
 *   <li>fades all the way IN,</li>
 *   <li>sits at FULL opacity for at least {@link TravelerConfig#minTimeOnScreenTicks}
 *       ticks (or its whole hold, if that is shorter),</li>
 *   <li>fades all the way OUT.</li>
 * </ol>
 * A title is never stamped over, and a fade out is never skipped. Crossing a border does
 * not cut the current title's opacity short; it ends the current title's HOLD, which is
 * the only part of the sequence that is safe to shorten.
 *
 * <h2>Why there is no minimum gap any more</h2>
 * There used to be a {@code minTicksBetweenTitles} gap, and a new title waited it out
 * before it could start. It stopped the spam, but it bought that with the two things the
 * owner disliked most: at the defaults a title arriving just after another one waited
 * two and a half seconds to appear, and when it did appear it REPLACED the one in front
 * of it, so the outgoing fade out never happened.
 *
 * <p>The gap is gone. What throttles a fast run now is the sequence itself: a title's
 * fade in, its guaranteed time at full opacity and its fade out. The fades play at their
 * full configured length unless the player turns on Faster Fades When Busy, which
 * shortens them in proportion to how quickly borders are arriving (see
 * {@link #paceScale()}). Shortening them was the 1.1.0 default, and it read as rushed.
 *
 * <h2>Nothing plays catch-up, and nothing brightens again</h2>
 * A title waiting for its turn is dropped if the player leaves its biome before it starts
 * ({@link #dropPending()}), so the screen only ever names where the player is. A title
 * that has started fading out always finishes fading out: coming back to its biome stops
 * anything else from being announced, but it does not bring the title back up. Doing that
 * reset its hold and read as the same title loading twice.
 *
 * <h2>The queue is still exactly one deep</h2>
 * A newer arrival REPLACES the one waiting rather than joining a line behind it, which
 * is the whole of "show the first and the last and skip the ones in between". A real
 * queue would play all of them back to back, seconds after the player left them.
 */
public final class TitleState {

    /** {@link #sinceLastStart} when nothing has been shown, so the next title is instant. */
    private static final int LONG_AGO = Integer.MAX_VALUE / 2;

    /**
     * The beat between a dimension title and the biome title behind it, in ticks.
     *
     * <p>A dimension title is not competing with the biome title, it is introducing it,
     * so the biome waits for the dimension to play in FULL - fade out included - and then
     * for this pause on top. Half a second is enough of a beat to feel deliberate without
     * becoming an empty screen.
     */
    private static final int AFTER_DIMENSION_PAUSE = 10;

    /**
     * The floor on a compressed fade, in ticks.
     *
     * <p>A fade of one or two ticks is a cut, not a fade, and at a low frame rate it can
     * fall entirely between two drawn frames. Three ticks is 0.15s and always renders.
     */
    private static final int MIN_FADE_TICKS = 3;

    /**
     * The floor on {@link #paceScale()}.
     *
     * <p>Without it, crossing borders every few ticks would drive the fades to nothing
     * and the titles would strobe. With it, the fastest the mod will ever run is a
     * 40% fade in, the guaranteed hold, and a 40% fade out.
     */
    private static final float MIN_PACE_SCALE = 0.4F;

    private enum Phase { GONE, FADE_IN, HOLD, FADE_OUT }

    private static Component text;
    private static TitleArt.Entry artwork;
    private static Phase phase = Phase.GONE;

    /** Opacity at the end of the current tick, and at the end of the last one. */
    private static float alpha;
    private static float alphaPrev;

    /** How much {@link #alpha} moves per tick while fading, always positive. */
    private static float step;

    /** Ticks of hold left, and ticks of hold already spent at full opacity. */
    private static int holdLeft;
    private static int heldTicks;

    /** Ticks still to wait before a title waiting behind a DIMENSION title may start. */
    private static int afterPause;

    /** Ticks since ANY title last started, whether or not it is still on screen. */
    private static int sinceLastStart = LONG_AGO;

    /**
     * Ticks since the game started, never reset, so the tracker can ask whether a title
     * started after a given moment. {@link #clear()} leaves it alone on purpose.
     */
    private static long clock;

    /** When each displayed name last STARTED, on {@link #clock}. */
    private static final Map<String, Long> startedAt = new HashMap<>();

    /**
     * The DISPLAYED NAME of the title that last started, kept after it has faded out.
     *
     * <p>Used to cancel a title that would follow itself: crossing four borders quickly
     * and ending up back where you began would otherwise play the first name twice.
     */
    private static String lastKey;
    private static boolean currentIsDimension;

    private static String pendingKey;
    private static Component pendingText;
    private static TitleArt.Entry pendingArtwork;
    private static boolean pendingIsDimension;
    private static float pendingScale = 1.0F;

    /** Set when something is waiting: end the current HOLD as soon as it is allowed to. */
    private static boolean cutWanted;
    private static float cutScale = 1.0F;

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
        float scale = paceScale();

        if (key.equals(lastKey) && (phase != Phase.GONE || pendingText != null)) {
            // BACK WHERE WE STARTED, so there is nothing to announce. Cross from Plains
            // into Forest and straight back, and this would otherwise play Plains over
            // the Plains title already on screen: the same name fading into the same
            // name, which reads as a glitch rather than as a border crossing.
            //
            // GONE WITH SOMETHING QUEUED COUNTS TOO. A title cut by the next one fades
            // out, reaches zero and is GONE for exactly one tick before the queued title
            // starts. Stepping back on that tick used to put this same name back in the
            // queue in place of the other one, so it replayed straight after fading out:
            // the "same biome loads again" that 1.0 had fixed with its minimum gap and
            // 1.1.0 brought back when the gap went.
            returnTo(key);
            return;
        }

        if (phase == Phase.GONE && afterPause <= 0 && pendingText == null) {
            begin(key, component, entry, dimension, scale);
            return;
        }

        pendingKey = key;
        pendingText = component;
        pendingArtwork = entry;
        pendingIsDimension = dimension;
        // BEHIND A DIMENSION TITLE, ALWAYS AT FULL LENGTH. The biome arrives the same tick
        // the dimension title starts, so the pace measured now says "a title started 0
        // ticks ago" and would squeeze the biome's fade in to three ticks, a flash, even
        // though it will only start seconds later. A portal is not fast travel.
        pendingScale = currentIsDimension || afterPause > 0 ? 1.0F : scale;

        // A DIMENSION TITLE IS NEVER CUT. It is introducing the biome title behind it,
        // so it plays in full and the biome waits out AFTER_DIMENSION_PAUSE on top.
        if (!currentIsDimension) {
            cutWanted = true;
            cutScale = scale;
        }
    }

    /**
     * The player is in a place whose title is not going to be announced again: it is
     * the title on screen, or it started too recently. Whatever was queued for the
     * places in between is dropped.
     *
     * <p>If the title on screen is this place's own, the trip cancels itself out: the
     * cut it asked for is cancelled, so a title still in its hold simply carries on. One
     * that has already started fading out FINISHES fading out. It used to be faded back
     * in and given a whole new hold, which on screen was the same title dimming and
     * coming back as if it had loaded again.
     *
     * <p>If the title on screen is for somewhere the player has left, it plays out, unless
     * End Title When You Leave is on: then it is cut short as if another title had
     * arrived, keeping its guaranteed hold and its full fade out. A dimension title is
     * never cut.
     */
    public static void returnTo(String key) {
        clearPending();
        if (phase == Phase.GONE) {
            cutWanted = false;
            return;
        }
        if (key.equals(lastKey)) {
            cutWanted = false;
            return;
        }
        cutWanted = TravelerConfig.endTitleWhenLeaving && !currentIsDimension;
        cutScale = paceScale();
    }

    /**
     * The player has arrived somewhere else, so a title still waiting for its turn names
     * a place they have left: drop it rather than play it late.
     *
     * <p>Without this the one-deep queue played catch-up: cross into a Forest while
     * another title held the screen, keep walking into a biome whose title is not shown
     * (too recent, silenced, a surface biome seen from a cave), and "Forest" appeared a
     * second or two later, about a place already behind the player.
     *
     * <p>The cut that the dropped title asked for is withdrawn too, so the title on screen
     * plays out, unless End Title When You Leave says otherwise. The tracker calls this
     * on every biome change it counts, before deciding whether to announce the new one;
     * an announcement simply queues itself again.
     */
    public static void dropPending() {
        if (pendingText == null || pendingIsDimension) {
            return;
        }
        clearPending();
        if (!TravelerConfig.endTitleWhenLeaving) {
            cutWanted = false;
        }
    }

    /** {@link #clock} now, for {@link #startedSince}. */
    public static long now() {
        return clock;
    }

    /** Has a title with this displayed name started at or after {@code tick}? */
    public static boolean startedSince(String key, long tick) {
        Long at = startedAt.get(key);
        return at != null && at >= tick;
    }

    /**
     * How far the fades are compressed for a title arriving now, 0..1.
     *
     * <p>This is the whole of "the sequence shortens when you move fast", and it needs no
     * speed, no distance and no biome size - only how long ago the last title started
     * against how long a full sequence takes. Cross a border after a long walk and the
     * ratio is 1, so nothing is compressed. Cross one a second after the last and the
     * fades run at a third of their length, which is what keeps a fast run moving.
     *
     * <p>It is SELF TUNING: a run of borders at a steady pace settles on a scale that
     * fits a whole title into the interval between them, because the interval is what
     * the scale is measured from. Nothing has to know an elytra from a walk.
     *
     * <p>OFF BY DEFAULT since 1.2.0, behind Faster Fades When Busy: the owner found the
     * shortened fades rushed, and preferred every fade at its full length.
     */
    private static float paceScale() {
        if (!TravelerConfig.fasterFadesWhenBusy) {
            return 1.0F;
        }
        int full = Math.max(1, TravelerConfig.fadeInTicks
                + TravelerConfig.holdTicks + TravelerConfig.fadeOutTicks);
        if (sinceLastStart >= full) {
            return 1.0F;
        }
        return Math.max(MIN_PACE_SCALE, sinceLastStart / (float) full);
    }

    /**
     * Ticks at full opacity a title is owed before anything may end its hold.
     *
     * <p>The SHORTER of the two, so the guarantee can never make a title outlive its own
     * configured hold: a player who sets Time on Screen to 5 gets 5, not the minimum.
     */
    private static int guaranteedHold() {
        return Math.min(Math.max(0, TravelerConfig.minTimeOnScreenTicks),
                Math.max(0, TravelerConfig.holdTicks));
    }

    private static float fadeInStep(float scale) {
        return 1.0F / Math.max(1, scaled(TravelerConfig.fadeInTicks, scale));
    }

    /**
     * A configured duration compressed by {@code scale}, floored so it stays a fade.
     *
     * <p>Zero in, zero out: a player who set a fade to 0 wants no fade, and the floor
     * must not put one back.
     */
    private static int scaled(int ticks, float scale) {
        if (ticks <= 0) {
            return 0;
        }
        if (scale >= 1.0F) {
            return ticks;
        }
        return Math.max(MIN_FADE_TICKS, Math.round(ticks * scale));
    }

    private static void begin(String key, Component component, TitleArt.Entry entry,
                              boolean dimension, float scale) {
        text = component;
        artwork = entry;
        lastKey = key;
        startedAt.put(key, clock);
        currentIsDimension = dimension;
        sinceLastStart = 0;
        cutWanted = false;
        afterPause = 0;
        clearPending();

        alpha = 0.0F;
        alphaPrev = 0.0F;
        if (scaled(TravelerConfig.fadeInTicks, scale) <= 0) {
            enterHold();
        } else {
            phase = Phase.FADE_IN;
            step = fadeInStep(scale);
        }
    }

    private static void enterHold() {
        alpha = 1.0F;
        phase = Phase.HOLD;
        holdLeft = Math.max(0, TravelerConfig.holdTicks);
        heldTicks = 0;
    }

    /**
     * End the hold and start fading out.
     *
     * <p>Only ever reached from HOLD, which is why the fade out always starts from full
     * opacity and is always seen in full. {@code scale} is 1 for a title reaching the
     * end of its own hold and the arriving title's pace scale for one being cut short.
     */
    private static void startFadeOut(float scale) {
        int duration = scaled(TravelerConfig.fadeOutTicks, scale);
        if (duration <= 0) {
            finish();
            return;
        }
        phase = Phase.FADE_OUT;
        step = 1.0F / duration;
    }

    private static void finish() {
        alpha = 0.0F;
        phase = Phase.GONE;
        text = null;
        artwork = null;
        cutWanted = false;
        afterPause = currentIsDimension ? AFTER_DIMENSION_PAUSE : 0;
    }

    private static void clearPending() {
        pendingKey = null;
        pendingText = null;
        pendingArtwork = null;
        pendingIsDimension = false;
        pendingScale = 1.0F;
    }

    /** Forget everything, including the clock. Leaving a world, or a render failure. */
    public static void clear() {
        text = null;
        artwork = null;
        phase = Phase.GONE;
        alpha = 0.0F;
        alphaPrev = 0.0F;
        step = 0.0F;
        holdLeft = 0;
        heldTicks = 0;
        afterPause = 0;
        sinceLastStart = LONG_AGO;
        lastKey = null;
        startedAt.clear();
        currentIsDimension = false;
        cutWanted = false;
        cutScale = 1.0F;
        clearPending();
    }

    public static void tick() {
        clock++;
        if (sinceLastStart < LONG_AGO) {
            sinceLastStart++;
        }

        if (phase == Phase.GONE) {
            // The pause runs out whether or not anything is waiting. Counting it down only
            // while a title was pending left it armed after a dimension title with no biome
            // behind it, so the next title, however much later, started half a second late.
            if (afterPause > 0) {
                afterPause--;
            } else if (pendingText != null) {
                begin(pendingKey, pendingText, pendingArtwork, pendingIsDimension,
                        pendingScale);
            }
            return;
        }

        alphaPrev = alpha;
        switch (phase) {
            case FADE_IN -> {
                alpha += step;
                if (alpha >= 1.0F) {
                    enterHold();
                }
            }
            case HOLD -> {
                heldTicks++;
                if (cutWanted && heldTicks >= guaranteedHold()) {
                    startFadeOut(cutScale);
                } else if (--holdLeft <= 0) {
                    startFadeOut(1.0F);
                }
            }
            case FADE_OUT -> {
                alpha -= step;
                if (alpha <= 0.0F) {
                    finish();
                }
            }
            default -> {
            }
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
     * <p>The tick loop moves opacity once per tick; this interpolates between the last
     * two so the fade is smooth at any frame rate rather than stepping twenty times a
     * second. Nothing here knows which phase the title is in, which is the point: a fade
     * that reverses direction mid-flight interpolates exactly like one that does not.
     */
    public static float alpha(float partialTick) {
        if (text == null) {
            return 0.0F;
        }
        return clamp(alphaPrev + (alpha - alphaPrev) * clamp(partialTick));
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }
}
