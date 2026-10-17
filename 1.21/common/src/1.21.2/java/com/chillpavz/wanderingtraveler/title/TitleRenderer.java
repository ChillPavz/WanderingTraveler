package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.Constants;
import com.chillpavz.wanderingtraveler.config.TravelerConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Draws the current title. Shared by both loaders; only the hook differs.
 *
 * <p>THE POSE IS A {@link PoseStack} HERE. GuiGraphics.pose() returns a PoseStack up to
 * 1.21.5 and a JOML Matrix3x2fStack from 1.21.6, which is the wall this band stops at. The
 * two are not interchangeable: PoseStack is 3D, so it wants a z on translate and scale, and
 * its push and pop are named pushPose and popPose rather than pushMatrix and popMatrix.
 */
public final class TitleRenderer {

    /** Kept clear of the screen edge, in GUI-scaled pixels. */
    private static final int EDGE_MARGIN = 8;

    /**
     * The lowest alpha this version can be trusted to draw, out of 255.
     *
     * <p><b>Font.adjustColor turns a nearly transparent colour FULLY OPAQUE at 1.21.</b>
     * It reads {@code (color & 0xFC000000) == 0} as "no alpha was given" and ORs in
     * 0xFF000000, so an alpha of 1, 2 or 3 does not render faintly, it renders at 100%.
     *
     * <p>A fade therefore passed through one frame of full opacity at EACH END: the title
     * popped in at full strength, dropped to nearly nothing, faded in properly, and popped
     * once more on the way out. That is the flicker that made it look like the title was
     * loading twice.
     *
     * <p>The clamp is gone from 1.21.6 onward, which is why only this band shows it, and
     * why the newer branches need no such guard. Skipping these values costs nothing:
     * three parts in 255 is invisible either way.
     */
    private static final int MIN_VISIBLE_ALPHA = 4;

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
        int opacityByte = Math.round(alpha * 255.0F);
        // Not "alpha <= 0": see MIN_VISIBLE_ALPHA. Drawing 1, 2 or 3 here would be drawing
        // at full opacity on this version.
        if (opacityByte < MIN_VISIBLE_ALPHA) {
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

        int opacity = opacityByte << 24;
        int rgb = TravelerConfig.textColor & 0xFFFFFF;

        // No shadow behind the artwork - it draws its own, in the art.
        boolean shadow = artwork == null && TravelerConfig.textShadow;

        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        if (artwork != null) {
            // One source pixel per unit inside the pose, so the whole image is one quad.
            // THE ARTWORK IS ALWAYS WHITE: only the alpha is the fade, because a colour
            // would only darken it.
            float unit = TitleImages.HEIGHT_UNITS / (float) artwork.height();
            pose.scale(unit, unit, 1.0F);
            drawImage(gfx, artwork, opacity | 0xFFFFFF);
            pose.popPose();
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
        pose.popPose();
    }

    /**
     * One blit of the whole image. From 1.21.2 {@code blit} takes the render type as a
     * function and a colour, whose alpha is the fade ({@code setColor} is gone).
     */
    private static void drawImage(GuiGraphics gfx, TitleArt.Entry artwork, int argb) {
        int w = artwork.width();
        int h = artwork.height();
        gfx.blit(RenderType::guiTextured, artwork.texture(), 0, 0, 0.0F, 0.0F, w, h, w, h, argb);
    }
}
