"""Turn the rendered Blockbench titles in `Wandering Traveler/art/` into the mod's title images.

ONE PNG PER TITLE, DRAWN AS A GUI TEXTURE
-----------------------------------------
Each title is a single PNG at `textures/title/<namespace>/<name>.png`, drawn by the mod
with a coloured GUI blit (the colour's alpha is the fade). Until Oct 2026 a title was a
bitmap font sliced into up to 16 strips of at most 256 px, because a font glyph may not
exceed the 256 px font sheet; that route, its -1 spacer and its seam arithmetic are kept
in the archived font-route copy of the repo. A GUI texture has no 256 px ceiling, so
none of that is needed: scale once, save once.

SIZE AND SHARPNESS
------------------
The mod draws every title HEIGHT_UNITS (12) GUI units tall, times its artwork scale, so
the letters are one size whatever the biome is called. `pixels / 12` is the number of
source pixels per GUI unit. On screen a title is `12 * artworkScale * guiScale` physical
pixels tall: at the default artwork scale of 3 that is 36 per GUI scale step, so 1:1
needs 24 px per unit at GUI scale 8 (288 px tall). The strips were capped at 21 by the
font sheet (GUI scale 7); an image is not.

It is a TARGET and never an upscale: a title whose export is shorter keeps its own
resolution, because enlarging it here adds nothing and costs jar size. The generator
names those titles and how wide a re-export would have to be.

ONE IMAGE PER TITLE, NAMED AFTER THE BIOME
------------------------------------------
The mod finds titles by listing `textures/title/` on every resource reload and maps
`<namespace>/<name>.png` to the biome or dimension `<namespace>:<name>`. A resource pack
adds or replaces a title by shipping one PNG under our namespace, with no code.

TITLES FROM OTHER MODS go in `art/<namespace>/<name>.png` and land at
`textures/title/<namespace>/<name>.png`, e.g. `art/alexscaves/toxic_caves.png`.

USAGE
    py -3 tools/make_titles.py            # writes into the monolith's shared resources
    py -3 tools/make_titles.py --check    # report only, write nothing
"""
import os
import shutil
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
# The rendered titles live outside the repo, in Wandering Traveler/art next to the monolith.
TITLES = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'art')
MOD = os.path.join(os.path.dirname(HERE), 'shared', 'resources', 'assets', 'wanderingtraveler')

# Every title is drawn this many GUI units tall (TitleImages.HEIGHT_UNITS in the mod).
HEIGHT_UNITS = 12

# Source pixels per GUI unit to aim for: 24 is 1:1 up to GUI scale 8 at artwork scale 3.
TARGET_PX_PER_UNIT = 24

# A GUI texture larger than the graphics card's maximum texture size fails to upload.
# 8192 is supported by every card that runs 1.20+; ours stay far below it.
MAX_TEXTURE = 8192

DIMENSIONS = {'overworld', 'the_end', 'the_nether'}


def trim(im):
    """Crop fully transparent margins, so the box the mod lays out is the artwork."""
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im


def scaled(im):
    """Scale to the target height, never up. Returns (image, px per unit)."""
    w, h = im.size
    sh = min(h, HEIGHT_UNITS * TARGET_PX_PER_UNIT)
    sw = max(1, round(w * sh / float(h)))
    if (sw, sh) != (w, h):
        im = im.resize((sw, sh), Image.LANCZOS)
    return im, sh / float(HEIGHT_UNITS)


def main():
    check = '--check' in sys.argv[1:]
    if not os.path.isdir(TITLES):
        raise SystemExit('no art directory at %s' % TITLES)
    if not os.path.isdir(MOD):
        raise SystemExit('no mod resource tree at %s' % MOD)

    tex_root = os.path.join(MOD, 'textures', 'title')
    if not check:
        # Wipe first, so a title that was renamed or dropped cannot linger in the jar.
        # The font folder is the old strip route's: it must not come back.
        shutil.rmtree(tex_root, ignore_errors=True)
        shutil.rmtree(os.path.join(MOD, 'font'), ignore_errors=True)

    # art/<name>.png is a vanilla biome; art/<namespace>/<name>.png another mod's.
    sources = [('minecraft', f[:-4], os.path.join(TITLES, f))
               for f in os.listdir(TITLES) if f.endswith('.png')]
    for ns in sorted(os.listdir(TITLES)):
        if os.path.isdir(os.path.join(TITLES, ns)):
            sources += [(ns, f[:-4], os.path.join(TITLES, ns, f))
                        for f in os.listdir(os.path.join(TITLES, ns)) if f.endswith('.png')]
    rows = []
    for ns, base, path in sorted(sources):
        name = base if ns == 'minecraft' else ns + ':' + base
        tex_dir = os.path.join(tex_root, ns)
        if not check:
            os.makedirs(tex_dir, exist_ok=True)
        src = Image.open(path).convert('RGBA')
        im, per_unit = scaled(trim(src))
        if max(im.size) > MAX_TEXTURE:
            raise SystemExit('%s would be %dx%d, over the %d px texture ceiling'
                             % (name, im.width, im.height, MAX_TEXTURE))
        if not check:
            im.save(os.path.join(tex_dir, base + '.png'), optimize=True)
        rows.append((name, src.size, im.size, per_unit,
                     'dimension' if name in DIMENSIONS else 'biome'))

    for name, s, size, per_unit, kind in rows:
        print('   %-28s %-10s %4dx%-4d -> %4dx%-4d  %5.2f px/unit'
              % (name, kind, s[0], s[1], size[0], size[1], per_unit))

    limited = [r for r in rows if r[3] < TARGET_PX_PER_UNIT]
    if limited:
        print('\n%d titles are limited by the EXPORT, so a re-export is the only way to '
              'sharpen them:' % len(limited))
        for name, s, size, per_unit, kind in sorted(limited, key=lambda r: r[3]):
            print('   %-28s %5.2f px/unit -> export %d px wide to reach %d'
                  % (name, per_unit, int(round(s[0] * TARGET_PX_PER_UNIT / per_unit)),
                     TARGET_PX_PER_UNIT))

    print('\n%d titles (%d dimension, %d biome), widest %d px, %.1f Mpx in total'
          % (len(rows), sum(1 for r in rows if r[4] == 'dimension'),
             sum(1 for r in rows if r[4] == 'biome'), max(r[2][0] for r in rows),
             sum(r[2][0] * r[2][1] for r in rows) / 1e6))
    print('%s %s' % ('would write into' if check else 'wrote into', tex_root))


if __name__ == '__main__':
    main()
