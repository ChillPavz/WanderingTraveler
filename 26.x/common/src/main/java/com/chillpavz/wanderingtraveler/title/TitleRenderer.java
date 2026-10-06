package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.Constants;
import com.chillpavz.wanderingtraveler.config.TravelerConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Draws the current title. Shared by both loaders; only the hook differs.
 *
 * <p>26.x has no {@code GuiGraphics}: a HUD element extracts into a
 * {@link GuiGraphicsExtractor} instead, and the pose is a JOML {@link Matrix3x2fStack}.
 */
public final class TitleRenderer {

    /** Kept clear of the screen edge, in GUI-scaled pixels. */
    private static final int EDGE_MARGIN = 8;

    private TitleRenderer() {
    }

    public static void render(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        // DEGRADE, NEVER CRASH. This runs every frame inside somebody else's render
        // pass; a title that fails to draw is a cosmetic loss, an exception here is not.
        try {
            draw(gfx, delta);
        } catch (Throwable t) {
            TitleState.clear();
            Constants.LOG.error("Title render failed, clearing it", t);
        }
    }

    private static void draw(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        if (!TravelerConfig.enabled || !TitleState.isShowing()) {
            return;
        }
        // NOTE: Options.hideGui is GONE at 26.2 - there is no such field on Options any
        // more. The F1 state is handled upstream of the HUD element list, so vanilla
        // simply does not visit our element while the GUI is hidden and there is nothing
        // to test here. Verify in game; if a title does show with F1 pressed, the flag
        // has moved somewhere else rather than disappeared.
        Minecraft minecraft = Minecraft.getInstance();

        float alpha = TitleState.alpha(delta.getGameTimeDeltaPartialTick(false));
        if (alpha <= 0.0F) {
            return;
        }

        Component text = TitleState.text();
        TitleArt.Entry artwork = TitleState.artwork();
        Font font = minecraft.font;

        // Both the artwork and the text are measured in FONT UNITS here, so the anchor
        // and the size bands work on the same numbers whichever path is in play.
        int width = font.width(text);
        // A title glyph is authored with ascent 7, the same as vanilla's own font, which
        // puts its top exactly on the draw y.
        int height = artwork != null ? artwork.height() : font.lineHeight;

        // THE SIZE BAND IS CHOSEN BY DRAWN WIDTH, which is the length of the name in the
        // only unit that matters on screen. Plain text takes one scale for every name:
        // it is already uniform by construction, and splitting it into bands would make
        // a mod that is meant to look consistent stop looking consistent.
        float scale = artwork != null
                ? TravelerConfig.artworkScaleFor(width)
                : TravelerConfig.textScale();

        int screenW = gfx.guiWidth();
        int screenH = gfx.guiHeight();

        // SHRINK TO FIT, and only the titles that need it. This is the backstop for a
        // very long modded name on a small screen at a large GUI scale, not the normal
        // path - the three size bands exist so that it stays rare, because a title that
        // silently changes size is the thing that makes several of them look inconsistent.
        int maxW = Math.max(1, screenW - 2 * EDGE_MARGIN);
        if (width * scale > maxW) {
            scale = maxW / (float) width;
        }

        int boxW = Math.round(width * scale);
        int boxH = Math.round(height * scale);

        int x = TravelerConfig.anchor.x(screenW, boxW) + TravelerConfig.offsetX;
        int y = TravelerConfig.anchor.y(screenH, boxH) + TravelerConfig.offsetY;

        // THE ARTWORK IS ALWAYS WHITE. A full-colour glyph is multiplied by the text
        // colour, so the colour option can only ever darken it; it applies to the plain
        // text path, which is greyscale and takes a tint properly.
        int opacity = Math.round(alpha * 255.0F) << 24;
        int rgb = artwork != null ? 0xFFFFFF : TravelerConfig.textColor & 0xFFFFFF;

        // No shadow behind the artwork either - it draws its own, in the art. The
        // incumbent's "black boxes" report turned out to be vanilla's Accessibility ->
        // Text Background setting interacting with its draw path, and drawing plain
        // glyphs cannot reproduce it.
        boolean shadow = artwork == null && TravelerConfig.textShadow;

        Matrix3x2fStack pose = gfx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        if (shadow) {
            // DRAWN BY HAND rather than through the draw call's shadow flag, because
            // that flag picks the colour itself - ARGB.scaleRGB(colour, 0.25f) - and
            // this one is configurable. One unit right and down, inside the scaled pose,
            // so the offset grows with the title exactly as vanilla's does.
            gfx.text(font, text, 1, 1,
                    opacity | TravelerConfig.shadowColorToDraw() & 0xFFFFFF, false);
        }
        gfx.text(font, text, 0, 0, opacity | rgb, false);
        pose.popMatrix();
    }
}
