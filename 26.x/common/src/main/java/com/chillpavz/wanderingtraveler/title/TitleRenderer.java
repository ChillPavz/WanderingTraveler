package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.Constants;
import com.chillpavz.wanderingtraveler.config.TravelerConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Draws the current title. Shared by both loaders; only the hook differs.
 *
 * <p>26.x has no {@code GuiGraphics}: a HUD element extracts into a
 * {@link GuiGraphicsExtractor} instead, and the pose is a JOML {@link Matrix3x2fStack}.
 */
public final class TitleRenderer {

    /** Kept clear of the screen edge, in GUI-scaled pixels. */
    private static final int EDGE_MARGIN = 8;

    /**
     * {@code GuiGraphicsExtractor.blit(RenderPipeline, Identifier, int x, int y, float u,
     * float v, int width, int height, int textureWidth, int textureHeight, int argb)},
     * found once by reflection, and the pipeline to pass it.
     *
     * <p><b>WHY REFLECTION: ONE JAR SERVES 26.1 TO 26.3, AND 26.3 MOVED THE TYPE.</b>
     * {@code RenderPipeline} is {@code com.mojang.blaze3d.pipeline} on 26.1 and 26.2,
     * {@code com.mojang.renderpearl.api.pipeline} on 26.3 (and back again on the 26.4
     * snapshots), so a direct call compiled here names the 26.2 class and throws
     * {@code NoSuchMethodError} on 26.3. The method's name and every other parameter are
     * the same on all of them, so the type is taken from the {@code GUI_TEXTURED} field
     * itself and never written down. 26.x is not obfuscated, so the name is stable.
     *
     * <p>DEGRADE, NEVER CRASH: if the lookup or a call fails, it logs once and every
     * title from then on draws as plain text.
     */
    private static Method blit;
    private static Object pipeline;
    private static boolean blitLookedUp;

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
        if (artwork != null && !blitAvailable()) {
            artwork = null;
        }
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
            // THE ARTWORK IS ALWAYS WHITE: the blit multiplies by the colour, so the
            // colour option could only darken it; only the alpha is the fade.
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
            gfx.text(font, text, 1, 1,
                    opacity | TravelerConfig.shadowColorToDraw() & 0xFFFFFF, false);
        }
        gfx.text(font, text, 0, 0, opacity | rgb, false);
        pose.popMatrix();
    }

    private static boolean blitAvailable() {
        if (!blitLookedUp) {
            blitLookedUp = true;
            try {
                Field field = RenderPipelines.class.getField("GUI_TEXTURED");
                Method method = GuiGraphicsExtractor.class.getMethod("blit", field.getType(),
                        Identifier.class, int.class, int.class, float.class, float.class,
                        int.class, int.class, int.class, int.class, int.class);
                pipeline = field.get(null);
                blit = method;
            } catch (Throwable t) {
                Constants.LOG.warn("No GUI blit found on this version; titles will show as "
                        + "plain text ({})", t.toString());
            }
        }
        return blit != null;
    }

    private static void drawImage(GuiGraphicsExtractor gfx, TitleArt.Entry artwork, int argb) {
        int w = artwork.width();
        int h = artwork.height();
        try {
            blit.invoke(gfx, pipeline, artwork.texture(), 0, 0, 0.0F, 0.0F, w, h, w, h, argb);
        } catch (Throwable t) {
            blit = null;
            Constants.LOG.warn("Title image draw failed; titles will show as plain text "
                    + "from now on ({})", t.toString());
        }
    }
}
