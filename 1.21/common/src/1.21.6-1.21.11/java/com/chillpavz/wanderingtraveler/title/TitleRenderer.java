package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.Constants;
import com.chillpavz.wanderingtraveler.config.TravelerConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Draws the current title. Shared by both loaders; only the hook differs.
 *
 * <p>{@link GuiGraphics} here, where the 26.x branch takes a {@code GuiGraphicsExtractor}:
 * the extract-then-render split lands at 26.1 and this version is below it. The pose is a
 * JOML {@link Matrix3x2fStack} on both, so only the type and the draw call differ.
 */
public final class TitleRenderer {

    /** Kept clear of the screen edge, in GUI-scaled pixels. */
    private static final int EDGE_MARGIN = 8;

    private TitleRenderer() {
    }

    public static void render(GuiGraphics gfx, DeltaTracker delta) {
        // DEGRADE, NEVER CRASH. This runs every frame inside somebody else's render
        // pass; a title that fails to draw is a cosmetic loss, an exception here is not.
        try {
            draw(gfx, delta);
        } catch (Throwable t) {
            TitleState.clear();
            Constants.LOG.error("Title render failed, clearing it", t);
        }
    }

    private static void draw(GuiGraphics gfx, DeltaTracker delta) {
        if (!TravelerConfig.enabled || !TitleState.isShowing()) {
            return;
        }
        // NOTE: Options.hideGui is GONE at 26.x - there is no such field on Options any
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
        int width = artwork != null ? artwork.widthUnits() : font.width(text);
        int height = artwork != null ? TitleImages.HEIGHT_UNITS : font.lineHeight;

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

        int opacity = Math.round(alpha * 255.0F) << 24;
        int rgb = TravelerConfig.textColor & 0xFFFFFF;

        // No shadow behind the artwork - it draws its own, in the art.
        boolean shadow = artwork == null && TravelerConfig.textShadow;

        Matrix3x2fStack pose = gfx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        if (artwork != null) {
            // One source pixel per unit inside the pose, so the whole image is one quad.
            // THE ARTWORK IS ALWAYS WHITE: only the alpha is the fade, because a colour
            // would only darken it.
            float unit = TitleImages.HEIGHT_UNITS / (float) artwork.height();
            pose.scale(unit, unit);
            drawImage(gfx, artwork, opacity | 0xFFFFFF);
            pose.popMatrix();
            return;
        }
        if (shadow) {
            // DRAWN BY HAND rather than through the draw call's shadow flag, because
            // that flag picks the colour itself - ARGB.scaleRGB(colour, 0.25f) - and
            // this one is configurable. One unit right and down, inside the scaled pose,
            // so the offset grows with the title exactly as vanilla's does.
            gfx.drawString(font, text, 1, 1,
                    opacity | TravelerConfig.shadowColorToDraw() & 0xFFFFFF, false);
        }
        gfx.drawString(font, text, 0, 0, opacity | rgb, false);
        pose.popMatrix();
    }

    /**
     * One blit of the whole image. From 1.21.6 {@code blit} takes a render pipeline and a
     * colour, whose alpha is the fade.
     */
    private static void drawImage(GuiGraphics gfx, TitleArt.Entry artwork, int argb) {
        int w = artwork.width();
        int h = artwork.height();
        gfx.blit(RenderPipelines.GUI_TEXTURED, artwork.texture(), 0, 0, 0.0F, 0.0F, w, h, w, h, argb);
    }
}
