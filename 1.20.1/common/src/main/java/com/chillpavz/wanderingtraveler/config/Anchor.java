package com.chillpavz.wanderingtraveler.config;

/**
 * Nine-way screen anchoring for the title.
 *
 * <p>The incumbent offers exactly two positions, top-left and centre, and its issue
 * tracker has had an open request for the other corners since 2024 with several people
 * agreeing. It is a day of work, so it ships in v1.
 *
 * <h2>Each anchor already clears the HUD, so the offsets start at zero</h2>
 * The first version put every anchor flush against its edge and leaned on a default
 * offset of 30 pixels down to make the top one look right. That is wrong for the other
 * six: the same 30 pixels pushed the middle row below the crosshair and the bottom row
 * off the screen entirely, behind the hotbar.
 *
 * <p>So the anchor itself is responsible for landing somewhere sensible - the top row
 * clear of the edge, the middle row centred on the crosshair, the bottom row above the
 * hotbar and the status bars - and {@code offsetX}/{@code offsetY} are a pure nudge that
 * defaults to nothing. A player who changes the anchor now gets a usable position without
 * having to re-tune the offsets, which is the whole point of having nine of them.
 *
 * <p>Offsets are in GUI-SCALED PIXELS, i.e. the same units the vanilla HUD is laid out
 * in, and they are applied AFTER the anchor. That is deliberate: the same complaint
 * thread reports that the incumbent's offsets are undocumented and that placement moves
 * when the GUI scale changes. Anchoring to a screen edge and offsetting in scaled pixels
 * keeps a title the same distance from that edge at every GUI scale.
 */
public enum Anchor {

    TOP_LEFT(-1, -1),
    TOP_CENTER(0, -1),
    TOP_RIGHT(1, -1),
    MIDDLE_LEFT(-1, 0),
    MIDDLE_CENTER(0, 0),
    MIDDLE_RIGHT(1, 0),
    BOTTOM_LEFT(-1, 1),
    BOTTOM_CENTER(0, 1),
    BOTTOM_RIGHT(1, 1);

    /** Clear of the left or right edge, in GUI-scaled pixels. */
    private static final int EDGE_MARGIN = 8;

    /** Clear of the top edge. The old default offset, now built into the anchor. */
    private static final int TOP_MARGIN = 30;

    /**
     * How much of the bottom of the screen the vanilla HUD owns, in GUI-scaled pixels.
     *
     * <p>The hotbar is 22 tall, the experience bar sits just above it, and the health,
     * armour and hunger rows above that reach to about 39. Clearing 45 puts a title above
     * all of it with a little air, which is what "above the hotbar but not too close to
     * it" asks for, and it still looks deliberate in creative where the status rows are
     * not drawn.
     */
    private static final int HOTBAR_CLEARANCE = 45;

    /** -1 left, 0 centre, 1 right. */
    private final int horizontal;
    /** -1 top, 0 middle, 1 bottom. */
    private final int vertical;

    Anchor(int horizontal, int vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    /** Left edge of a box `width` wide, anchored inside a screen `screenWidth` wide. */
    public int x(int screenWidth, int width) {
        return switch (this.horizontal) {
            case -1 -> EDGE_MARGIN;
            case 1 -> screenWidth - width - EDGE_MARGIN;
            default -> Math.round((screenWidth - width) * 0.5F);
        };
    }

    /**
     * Top edge of a box `height` tall, anchored inside a screen `screenHeight` tall.
     *
     * <p>The middle row centres the BOX on the screen's centre, so the title straddles
     * the crosshair rather than starting at it.
     */
    public int y(int screenHeight, int height) {
        return switch (this.vertical) {
            case -1 -> TOP_MARGIN;
            case 1 -> screenHeight - height - HOTBAR_CLEARANCE;
            default -> Math.round((screenHeight - height) * 0.5F);
        };
    }

    /**
     * A translation key, because Cloth labels an enum constant with
     * {@code Component.translatable(constant.toString())}.
     *
     * <p>Read out of {@code DefaultGuiProviders} in cloth-config 11.1.136: unless the
     * enum implements one of Cloth's own interfaces - which common cannot, it has no
     * loader dependency - the label is the result of {@code toString()} put through the
     * translation table. So the choice is between overriding this and showing the player
     * a raw {@code TOP_CENTER}.
     *
     * <p>Safe for the config FILE, which is Gson's business: Gson writes an enum by
     * {@code name()}, so the stored value stays {@code TOP_CENTER} and is unaffected.
     */
    @Override
    public String toString() {
        return "wanderingtraveler.anchor." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
