package com.chillpavz.wanderingtraveler.title;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The parts of finding a title image that name no Minecraft type, so every band shares them.
 *
 * <h2>One PNG per title</h2>
 * A title is {@code assets/wanderingtraveler/textures/title/<namespace>/<name>.png}, the
 * artwork for the biome or dimension {@code <namespace>:<name>}, drawn as a GUI texture. A
 * resource pack adds or replaces a title by shipping that one file; nothing lists them.
 */
public final class TitleImages {

    /**
     * Every title is drawn this many GUI units tall before the artwork scale, so the letters
     * are one size whatever the biome is called. 12 matches the old font route, which keeps
     * the size settings players already chose.
     */
    public static final int HEIGHT_UNITS = 12;

    /** Under our namespace's assets. */
    public static final String DIR = "textures/title";

    private static final String SUFFIX = ".png";
    private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;
    private static final int IHDR = 0x49484452;

    private TitleImages() {
    }

    /**
     * {@code textures/title/<namespace>/<name>.png} to {@code {namespace, name}}, or null
     * when the path is not exactly that shape.
     */
    public static String[] subjectOf(String path) {
        if (!path.startsWith(DIR + "/") || !path.endsWith(SUFFIX)) {
            return null;
        }
        String rel = path.substring(DIR.length() + 1, path.length() - SUFFIX.length());
        int slash = rel.indexOf('/');
        if (slash <= 0 || slash == rel.length() - 1) {
            return null;
        }
        return new String[] {rel.substring(0, slash), rel.substring(slash + 1)};
    }

    /**
     * Width and height out of the PNG header, without decoding the image: the size is all
     * layout needs, and the texture itself is only loaded the first time it is drawn.
     */
    public static int[] size(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        if (data.readLong() != PNG_SIGNATURE) {
            throw new IOException("not a PNG");
        }
        data.readInt();
        if (data.readInt() != IHDR) {
            throw new IOException("PNG without an IHDR chunk first");
        }
        int width = data.readInt();
        int height = data.readInt();
        if (width <= 0 || height <= 0) {
            throw new IOException("PNG of " + width + "x" + height);
        }
        return new int[] {width, height};
    }

    /** The title's width in GUI units when it is drawn {@link #HEIGHT_UNITS} tall. */
    public static int widthUnits(int width, int height) {
        return Math.max(1, Math.round(width * HEIGHT_UNITS / (float) height));
    }
}
