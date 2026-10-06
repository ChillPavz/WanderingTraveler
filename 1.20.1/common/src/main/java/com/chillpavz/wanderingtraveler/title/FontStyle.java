package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.Constants;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Applies a font to a {@link Style}.
 *
 * <h2>Why this exists, and why it is inert on this band</h2>
 * {@code Style.withFont} changes signature further up the line: it takes a
 * {@code ResourceLocation} through 1.21.8 and a {@code FontDescription} from 1.21.9, and
 * the 1.21.6-1.21.11 jar crosses that wall in the middle of its own range.
 *
 * <p>1.20 and 1.20.1 are entirely on the OLD form - verified against the real jar, where
 * {@code Style.withFont(ResourceLocation)} is the only single-argument method returning a
 * Style - so the resolution below always takes the direct branch here. It is kept rather
 * than simplified away because this file is shared, unedited, with every other branch,
 * and one identical copy across five branches is worth more than ten lines saved on one.
 *
 * <h2>Why it resolves by SHAPE and never by name</h2>
 * On Fabric the runtime name of {@code withFont} is {@code method_27706} and the runtime
 * name of {@code FontDescription} is a {@code class_} number, because the jar is remapped
 * to intermediary. A lookup by either name finds nothing there while working perfectly on
 * NeoForge and Forge, which is the worst possible shape of bug: it would pass every test
 * on two loaders out of three.
 *
 * <p>So the method is found by its SIGNATURE instead, which survives remapping:
 * <ul>
 *   <li>a method on {@code Style} returning {@code Style} and taking exactly one
 *       {@code ResourceLocation} is the old form, and is called directly;</li>
 *   <li>otherwise a method returning {@code Style} whose single parameter type declares a
 *       nested class with a {@code (ResourceLocation)} constructor is the new form, and
 *       that nested class is {@code FontDescription.Resource}.</li>
 * </ul>
 * {@code ResourceLocation.class} is itself remapped along with our own code, so comparing
 * against it is safe on every loader.
 *
 * <h2>Degrade, never crash</h2>
 * If neither shape is found the style is returned unchanged and {@link #isAvailable()}
 * reports false, so the caller falls back to plain text rather than drawing a row of
 * missing-glyph boxes. Resolution happens once and is cached.
 */
public final class FontStyle {

    private static boolean resolved;
    private static Method withFont;
    /** Null when {@code withFont} takes the identifier directly. */
    private static Constructor<?> descriptionCtor;

    private FontStyle() {
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        Method direct = null;
        Method wrapped = null;
        Constructor<?> ctor = null;

        for (Method m : Style.class.getMethods()) {
            if (m.getReturnType() != Style.class || m.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = m.getParameterTypes()[0];
            if (param == ResourceLocation.class) {
                direct = m;
                continue;
            }
            if (param.isPrimitive() || param.getName().startsWith("java.")) {
                continue;
            }
            // FontDescription is an interface whose Resource record wraps an identifier.
            for (Class<?> nested : param.getDeclaredClasses()) {
                for (Constructor<?> c : nested.getConstructors()) {
                    if (c.getParameterCount() == 1
                            && c.getParameterTypes()[0] == ResourceLocation.class) {
                        wrapped = m;
                        ctor = c;
                        break;
                    }
                }
                if (ctor != null) {
                    break;
                }
            }
        }

        if (direct != null) {
            withFont = direct;
            Constants.LOG.debug("Style.withFont takes an identifier directly");
        } else if (wrapped != null) {
            withFont = wrapped;
            descriptionCtor = ctor;
            Constants.LOG.debug("Style.withFont takes a font description");
        } else {
            Constants.LOG.error("No usable Style.withFont on this version; titles will "
                    + "fall back to plain text");
        }
    }

    /** False when no font can be applied, so the caller must not draw glyph codepoints. */
    public static boolean isAvailable() {
        resolve();
        return withFont != null;
    }

    /** The style with the font applied, or the style unchanged if that is impossible. */
    public static Style withFont(Style style, ResourceLocation font) {
        resolve();
        if (withFont == null) {
            return style;
        }
        try {
            Object arg = descriptionCtor == null ? font : descriptionCtor.newInstance(font);
            return (Style) withFont.invoke(style, arg);
        } catch (Throwable t) {
            Constants.LOG.error("Applying the title font failed", t);
            return style;
        }
    }
}
