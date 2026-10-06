"""Audit the BUILT JARS, which is the only check that sees what the game sees.

Every fault this catches has already shipped once. The font pack failed in game with
nothing but missing-glyph boxes on screen and 66 identical stack traces in the log,
because of two separate bugs that a source-level review had already walked past:

  * the provider type was written as "minecraft:bitmap". Most Minecraft registries take
    a namespaced id and THIS ONE DOES NOT - the codec answers
    `JsonParseException: Unknown element name:minecraft:bitmap` and every font in the
    pack fails. Vanilla's own font files use the bare name.
  * the font id carried a second "font/". A font id resolves ns:path back to
    assets/ns/font/path.json, so the prefix must be stripped, and an unknown font id
    throws nothing and logs nothing - it just draws missing-glyph boxes.

The seam check was added after the same class of fault shipped a third time: 22 of the
66 titles left a fraction of a font unit between their strips, and the ones whose error
was positive drew a vertical tear straight through the letters. Nothing in the game
reports that either, so it is measured here from the jar's own PNGs using vanilla's
arithmetic.

The config checks were added with the config screen. A renamed field is not a compile
error and does not log: it just shows the player a raw translation key where an option
name should be, and the two loader copies of the annotated class silently drift apart.

USAGE
    py -3 tools/audit_jar.py
"""
import json
import os
import re
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_ID = 'wanderingtraveler'

# The provider names vanilla's codec actually accepts, read out of the 26.2 client jar
# by grepping every assets/minecraft/font/**.json for "type". A namespaced form is
# rejected, so this list is bare names on purpose.
VALID_PROVIDERS = {'bitmap', 'reference', 'space', 'ttf', 'unihex'}

# FontTexture.SIZE. A glyph larger than this in either dimension is dropped silently.
MAX_GLYPH_PX = 256

# The font-unit height every title glyph declares. The seam arithmetic below is
# meaningless without it, so the checks further down fail loudly if a shipped font
# ever disagrees with this number.
DECLARED_HEIGHT = 12

# Cloth's own i18n scheme, read out of ConfigScreenProvider in cloth-config 11.1.136:
# "text.autoconfig.%s" then ".title", ".category.%s", ".option.%s", and a tooltip at
# "<option>.@Tooltip" or "<option>.@Tooltip[n]".
CONFIG_CLASS = 'com/chillpavz/wanderingtraveler/config/TravelerClothConfig.class'
HOLDER_CLASS = 'com/chillpavz/wanderingtraveler/config/TravelerConfig.class'
I18N = 'text.autoconfig.%s' % MOD_ID
LANG = 'assets/%s/lang/en_us.json' % MOD_ID

# Enums whose constants Cloth labels through toString(), which the common enums override
# to return a translation key. A constant added without a lang entry shows the player a
# raw key, and nothing logs it.
ENUM_LABELS = {
    'com/chillpavz/wanderingtraveler/config/Anchor.class': 'wanderingtraveler.anchor.',
    'com/chillpavz/wanderingtraveler/config/TravelerConfig$UndergroundTitles.class':
        'wanderingtraveler.underground.',
    'com/chillpavz/wanderingtraveler/config/TravelerConfig$ShowTitleWhen.class':
        'wanderingtraveler.showwhen.',
}

# TWO LOADERS ON THIS BAND, NOT THREE. NeoForge at 1.20 and 1.20.1 is 47.1.x, a soft
# fork still on net.minecraftforge packages that PROVIDES the `forge` mod id, so the
# Forge jar IS the NeoForge jar and there is no third module to audit.
ENTRYPOINTS = {
    'fabric': 'com.chillpavz.wanderingtraveler.WanderingTravelerFabric',
    'forge': 'com.chillpavz.wanderingtraveler.WanderingTravelerForge',
}

# The loader metadata file each module ships. Putting NeoForge's `type = "required"`
# syntax in Forge's file makes Forge reject the jar outright.
TOML = {
    'forge': 'META-INF/mods.toml',
}

# Mojang requires Java 17 at 1.20.x, so every class in both jars must be class-file
# major 61. A toolchain that quietly targeted something newer produces a jar that
# installs and then throws UnsupportedClassVersionError on the player's machine.
CLASS_MAJOR = 61

# Cloth's mod id, which is not the same on both loaders: the Fabric build of 11.1.136 is
# `cloth-config` and PROVIDES `cloth-config2`, while the Forge build is `cloth_config`.
CLOTH_IDS = ('cloth-config', 'cloth-config2', 'cloth_config')


def png_size(data):
    """Width and height from a PNG header, without needing Pillow."""
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        return None
    return struct.unpack('>II', data[16:24])


# --------------------------------------------------------------------------- class files
# Enough of the class file format to read a field table, so the config checks below can
# work from the JAR rather than from the source tree. Everything else in this file is an
# artefact check and these should be too: the source is not what ships.
_CP_SKIP = {3: 4, 4: 4, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
            16: 2, 17: 4, 18: 4, 19: 2, 20: 2, 15: 3}

ACC_STATIC = 0x0008
ACC_FINAL = 0x0010
ACC_SYNTHETIC = 0x1000
ACC_ENUM = 0x4000


def class_fields(data):
    """(name, access_flags) for every field declared in a class file."""
    pos = 8                                   # magic, minor, major
    count = struct.unpack('>H', data[pos:pos + 2])[0]
    pos += 2
    pool = {}
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            n = struct.unpack('>H', data[pos:pos + 2])[0]
            pool[i] = data[pos + 2:pos + 2 + n].decode('utf-8')
            pos += 2 + n
        elif tag in (5, 6):                   # long and double take two slots
            pos += 8
            i += 1
        else:
            pos += _CP_SKIP[tag]
        i += 1
    pos += 6                                  # access, this_class, super_class
    ifaces = struct.unpack('>H', data[pos:pos + 2])[0]
    pos += 2 + 2 * ifaces
    nfields = struct.unpack('>H', data[pos:pos + 2])[0]
    pos += 2
    out = []
    for _ in range(nfields):
        access, name_index = struct.unpack('>HH', data[pos:pos + 4])
        pos += 6                              # + descriptor_index
        nattrs = struct.unpack('>H', data[pos:pos + 2])[0]
        pos += 2
        for _ in range(nattrs):
            length = struct.unpack('>I', data[pos + 2:pos + 6])[0]
            pos += 6 + length
        out.append((pool[name_index], access))
    return out


def audit(jar_path, loader, problems):
    zf = zipfile.ZipFile(jar_path)
    names = set(zf.namelist())

    def fail(msg):
        problems.append('%s: %s' % (os.path.basename(jar_path), msg))

    # --- entry point ------------------------------------------------------
    entry = ENTRYPOINTS[loader]
    if entry.replace('.', '/') + '.class' not in names:
        fail('entry point %s is not in the jar' % entry)

    # --- metadata ---------------------------------------------------------
    if loader == 'fabric':
        meta = json.loads(zf.read('fabric.mod.json'))
        declared = [c for v in meta['entrypoints'].values() for c in v]
        for c in declared:
            if c.replace('.', '/') + '.class' not in names:
                fail('fabric.mod.json names a missing entry point %s' % c)
        if meta.get('environment') != 'client':
            fail('fabric.mod.json environment is %r, expected "client"'
                 % meta.get('environment'))
        if '\\n' in json.dumps(meta):
            fail('fabric.mod.json contains a literal backslash-n, so the template '
                 'escaping bug is back')
        # CLOTH IS OPTIONAL ON THIS BAND AND ON THIS BAND ALONE. It has no
        # neoforge-tagged file at 1.20 or 1.20.1, and the Forge jar here is also the
        # NeoForge jar, so a required dependency would refuse to start a NeoForge
        # player's game. Fabric is not itself affected, but the two metadata files have
        # to agree or the mod behaves differently on each loader for no stated reason.
        for cloth in CLOTH_IDS:
            if cloth in meta.get('depends', {}):
                fail('fabric.mod.json REQUIRES %s; cloth is optional on this band' % cloth)
        if not any(c in meta.get('recommends', {}) for c in CLOTH_IDS):
            fail('fabric.mod.json does not mention cloth at all, so nothing points a '
                 'player at the config screen')
    else:
        name = TOML[loader]
        toml = zf.read(name).decode()
        for line in toml.splitlines():
            if '${' in line:
                fail('%s has an unexpanded placeholder: %s' % (name, line.strip()))
        # Forge uses `mandatory = true`; NeoForge's `type = "required"` parses as an
        # unknown field and Forge refuses the jar with "Missing required field mandatory".
        #
        # COMMENTS ARE STRIPPED FIRST. The first version of this check searched the whole
        # file and matched the comment in mods.toml that explains this very rule, which is
        # the "anchor on the construct, never on a bare string" trap in its purest form.
        if loader == 'forge':
            live = [l.split('#', 1)[0] for l in toml.splitlines()]
            if any('type' in l and 'required' in l for l in live):
                fail('mods.toml uses the NeoForge dependency syntax; Forge needs '
                     'mandatory = true, not type = required')
            if not any('mandatory' in l for l in live):
                fail('mods.toml declares no mandatory field on its dependencies')
            # See the matching note on fabric.mod.json: cloth has no neoforge file at
            # 1.20.x and this jar is the NeoForge jar too, so requiring it would be a
            # hard startup failure for every NeoForge player.
            body = chr(10).join(live)
            # Split on the section headers rather than matching a regex across
            # them: a dependency block runs to the next [[ and there is no need
            # to be clever about it.
            blocks = body.split('[[dependencies.')
            cloth = [b for b in blocks if 'cloth_config' in b]
            if not cloth:
                fail('mods.toml does not declare cloth_config at all')
            elif 'mandatory = false' not in cloth[0]:
                fail('mods.toml makes cloth_config mandatory; it must be optional on '
                     'this band or NeoForge players cannot start the game')
            # A client-only mod with no displayTest marks every server incompatible.
            if 'IGNORE_ALL_VERSION' not in body:
                fail('mods.toml sets no displayTest, so this client-only mod reports '
                     'every server as a version mismatch')
        svc = 'META-INF/services/com.chillpavz.wanderingtraveler.platform.services.IPlatformHelper'
        if svc not in names:
            fail('the platform service file is missing or still named after the template')
        elif 'com.example' in zf.read(svc).decode():
            fail('the service file was RENAMED but its CONTENTS still name the template '
                 'package')

    # --- the icon ---------------------------------------------------------
    # Both metadata files name it, and a missing or non-square one is the kind of thing
    # that is only noticed on the store page after upload.
    icon = 'assets/%s/icon.png' % MOD_ID
    if icon not in names:
        fail('%s is missing, but both metadata files point at it' % icon)
    else:
        size = png_size(zf.read(icon))
        if size is None:
            fail('%s is not a PNG' % icon)
        elif size[0] != size[1]:
            fail('%s is %dx%d; both platforms want it square' % (icon, size[0], size[1]))
        elif size[0] < 128:
            fail('%s is only %d px; a project page shows it larger than that'
                 % (icon, size[0]))

    # --- the licence ------------------------------------------------------
    # THE JAR IS THE THING TO CHECK, NOT THE REPO ROOT. multiloader-common copies LICENSE
    # into every jar, and jared's template ships CC0 - a public domain dedication, i.e.
    # the exact opposite of this mod's terms. The 26.x branch shipped that mismatch in
    # every build until it was caught by hand, with both metadata files meanwhile
    # declaring All Rights Reserved.
    licences = [n for n in names if n.startswith('LICENSE')]
    if not licences:
        fail('no LICENSE file in the jar, though the build is supposed to copy one in')
    else:
        body = zf.read(licences[0]).decode('utf-8', 'ignore')
        if 'All rights reserved' not in body:
            fail('%s is not the All Rights Reserved licence this mod ships; its first '
                 'line is %r' % (licences[0], body.splitlines()[0] if body else ''))
        for wrong in ('CC0', 'Creative Commons', 'PolyForm', 'MIT License'):
            if wrong in body:
                fail('%s still contains %r, so it is not the licence this mod ships'
                     % (licences[0], wrong))

    if 'pack.mcmeta' not in names:
        fail('pack.mcmeta is missing')
    else:
        pack = json.loads(zf.read('pack.mcmeta'))['pack']
        # ONE FIELD ON THIS BAND, NOT FOUR. 1.20.x knows only pack_format:
        # supported_formats arrives at 1.21 and min_format/max_format later still, so the
        # four-field form the newer branches ship would be read by nothing here. The value
        # is 15, out of pack_version.resource in the client jar's version.json, and 1.20
        # is 15 as well.
        if 'pack_format' not in pack:
            fail('pack.mcmeta has no pack_format')
        elif pack['pack_format'] != 15:
            fail('pack.mcmeta declares pack_format %r; 1.20 and 1.20.1 are both 15'
                 % pack['pack_format'])
        for field in ('supported_formats', 'min_format', 'max_format'):
            if field in pack:
                fail('pack.mcmeta declares %s, which no version in this band reads; it '
                     'was copied down from a newer branch' % field)

    # --- the config screen ------------------------------------------------
    if CONFIG_CLASS not in names:
        fail('the Cloth config class is not in the jar, so there is no settings screen')
    elif LANG not in names:
        fail('%s is missing, so every option shows a raw translation key' % LANG)
    else:
        lang = json.loads(zf.read(LANG))
        if I18N + '.title' not in lang:
            fail('%s has no %s.title, so the screen is named after the mod id'
                 % (LANG, I18N))

        # EVERY DECLARED FIELD, not just the instance ones. ConfigScreenProvider walks
        # getDeclaredFields() with no filter for static, transient or synthetic, so a
        # static bookkeeping flag in that class becomes a real entry in the screen: an
        # untranslated toggle in an untranslated "default" tab. That shipped once.
        options = [name for name, access in class_fields(zf.read(CONFIG_CLASS))]
        for option in options:
            key = '%s.option.%s' % (I18N, option)
            if key not in lang:
                fail('the option %r has no %s, so the screen shows the player that key '
                     'instead of a name' % (option, key))
            # A tooltip is optional, but a GAP in an indexed one is not: Cloth stops at
            # the first missing index, so a typo silently truncates the explanation.
            if key + '.@Tooltip[0]' in lang:
                i = 0
                while '%s.@Tooltip[%d]' % (key, i) in lang:
                    i += 1
                if '%s.@Tooltip[%d]' % (key, i + 1) in lang:
                    fail('%s has a gap in its numbered tooltip lines at [%d]' % (key, i))

        # EVERY OPTION THE MOD READS MUST BE EDITABLE. TravelerConfig's mutable
        # statics are exactly the settings the rest of the mod consults, so one that
        # never got a field in the annotated class is an option the player cannot
        # reach - and it looks like a working default rather than a missing control.
        # Counting is enough to catch it: adding one without the other is the mistake.
        holder = [name for name, access in class_fields(zf.read(HOLDER_CLASS))
                  if access & ACC_STATIC and not access & (ACC_FINAL | ACC_SYNTHETIC)]
        if len(holder) != len(options):
            fail('TravelerConfig has %d settings but the config screen offers %d, so at '
                 'least one is not editable in game (holder: %s)'
                 % (len(holder), len(options), sorted(holder)))

        # A key with no field behind it is a rename that only got done on one side, and
        # it is invisible: the screen simply never asks for it.
        known = {'%s.option.%s' % (I18N, o) for o in options}
        for key in lang:
            if key.startswith(I18N + '.option.'):
                base = key.split('.@')[0]      # .@Tooltip, .@Tooltip[n], .@PrefixText
                if base not in known:
                    fail('%s names an option that no longer exists in the config class'
                         % key)

        for cls, prefix in ENUM_LABELS.items():
            if cls not in names:
                fail('%s is not in the jar' % cls)
                continue
            for name, access in class_fields(zf.read(cls)):
                if not access & ACC_ENUM:
                    continue
                key = prefix + name.lower()
                if key not in lang:
                    fail('the enum constant %s has no %s, so the screen offers the '
                         'player a raw key to choose from' % (name, key))

    # --- the jar was actually remapped, and to the right names ------------
    #
    # THIS IS A 1.20.x CHECK AND THE NEWER BRANCHES DO NOT NEED IT.
    #
    # Forge 1.20.1 runs on SRG member names, so a Forge jar compiled against the official
    # names has to be reobfuscated back before it will run. That is what `finalizedBy
    # reobfJar` in forge/build.gradle does, and if it ever stops running the build is
    # still green, the jar still installs, and the game dies at first use with
    # NoSuchMethodError. Forge from 1.20.6 needs none of this, which is why no other
    # branch here carries the line or the check.
    #
    # Fabric is the mirror image: Loom remaps our code to INTERMEDIARY on the way out, so
    # a Fabric jar that still names Minecraft members the way the source did means
    # remapJar did not run.
    ours = [n for n in names
            if n.startswith('com/chillpavz/wanderingtraveler/') and n.endswith('.class')]
    if not ours:
        fail("none of the mod's own classes are in the jar")
    else:
        blob = b''.join(zf.read(n) for n in ours)
        if loader == 'forge':
            if not re.search(rb'm_\d+_', blob):
                fail("no SRG member names anywhere in the mod's classes, so reobfJar "
                     "did not run; this jar will die with NoSuchMethodError at first use")
        else:
            if not re.search(rb'method_\d+', blob):
                fail("no intermediary member names in the mod's classes, so remapJar "
                     "did not run")

    # --- the class-file target --------------------------------------------
    #
    # Mojang requires Java 17 at this Minecraft version, so every class must be major 61.
    # A toolchain that quietly targeted something newer produces a jar that installs and
    # then throws UnsupportedClassVersionError on the player's machine, which is not
    # something any other check here would see.
    wrong = {}
    for name in ours:
        head = zf.read(name)[:8]
        if len(head) >= 8:
            major = struct.unpack('>H', head[6:8])[0]
            if major != CLASS_MAJOR:
                wrong[name] = major
    if wrong:
        fail('%d class(es) are not class-file major %d (Java 17): %s'
             % (len(wrong), CLASS_MAJOR, sorted(wrong.items())[:3]))

    # --- no mixins, on purpose -------------------------------------------
    stray = [n for n in names if n.endswith('mixins.json')]
    if stray:
        fail('mixin configs are declared but this mod ships none: %s' % stray)

    # --- the titles -------------------------------------------------------
    fonts = sorted(n for n in names
                   if n.startswith('assets/%s/font/' % MOD_ID) and n.endswith('.json'))
    if not fonts:
        fail('no title font definitions in the jar at all')

    for font in fonts:
        # The game turns assets/<ns>/font/<path>.json into the font id <ns>:<path>, so
        # that is the id the mod has to produce. Asserting the shape here is what stops
        # the extra-"font/" bug coming back through a refactor.
        rel = font[len('assets/%s/font/' % MOD_ID):-len('.json')]
        parts = rel.split('/')
        if len(parts) != 3 or parts[0] != 'title':
            fail('%s is not title/<namespace>/<name>.json, so the mod cannot derive a '
                 'biome id from it' % font)

        try:
            data = json.loads(zf.read(font))
        except Exception as e:
            fail('%s does not parse: %s' % (font, e))
            continue

        for provider in data.get('providers', []):
            kind = provider.get('type')
            if kind not in VALID_PROVIDERS:
                fail('%s uses provider type %r; vanilla accepts only %s and REJECTS the '
                     'namespaced form' % (font, kind, sorted(VALID_PROVIDERS)))
            if kind != 'bitmap':
                continue

            ascent = provider.get('ascent')
            height = provider.get('height', 8)
            if ascent is None:
                fail('%s declares no ascent' % font)
            elif ascent > height:
                fail('%s has ascent %d above height %d, which vanilla rejects'
                     % (font, ascent, height))

            ref = provider.get('file', '')
            if ':' not in ref:
                fail('%s has an unnamespaced file %r' % (font, ref))
                continue
            ns, path = ref.split(':', 1)
            texture = 'assets/%s/textures/%s' % (ns, path)
            if texture not in names:
                fail('%s points at %s, which is not in the jar' % (font, ref))
                continue
            size = png_size(zf.read(texture))
            if size is None:
                fail('%s is not a PNG' % texture)
                continue
            if max(size) > MAX_GLYPH_PX:
                fail('%s is %dx%d, over the %d px font sheet - it would be dropped with '
                     'no log line' % (texture, size[0], size[1], MAX_GLYPH_PX))
                continue

            # THE SEAM. Vanilla draws a bitmap glyph `pixelWidth * scale` wide and then
            # advances `round(actualWidth * scale) + 1`, with scale = height/imageHeight;
            # the font's -1 space provider cancels the +1, so whatever `round` moved is
            # left as a gap between the strips - a vertical tear straight through the
            # letters - or as an overlap. The check is on the imageHeight, because that
            # is what makes `stripWidth * scale` land on a whole number.
            #
            # `actualWidth` is assumed equal to the strip width here, which holds
            # because a strip is only ever cut where the artwork reaches both of its
            # edges. Proving it outright would mean decoding the PNG's pixels, which
            # this check deliberately does not do.
            w, h = size
            if h % height:
                fail('%s is %d px tall, which is not a whole number of the %d font units '
                     'it declares, so every seam in this title leaks a fraction of a unit'
                     % (texture, h, height))
            elif w % (h // height):
                fail('%s is %d px wide, which is not a whole number of font units at an '
                     'oversample of %d, so its seam does not close'
                     % (texture, w, h // height))

    # A title is several glyphs, so the count to check is BITMAP PROVIDERS against
    # textures, not fonts against textures. Anything left over is a strip from an
    # earlier run that nothing points at - dead weight in the jar that no other check
    # would notice.
    textures = {n for n in names
                if n.startswith('assets/%s/textures/title/' % MOD_ID) and n.endswith('.png')}
    referenced = set()
    for font in fonts:
        try:
            data = json.loads(zf.read(font))
        except Exception:
            continue
        for provider in data.get('providers', []):
            ref = provider.get('file')
            if provider.get('type') == 'bitmap' and ref and ':' in ref:
                ns, path = ref.split(':', 1)
                referenced.add('assets/%s/textures/%s' % (ns, path))
    print('   %-10s %3d titles, %3d strips, %3d textures'
          % (loader, len(fonts), len(referenced), len(textures)))
    orphans = sorted(textures - referenced)
    if orphans:
        fail('%d texture(s) no font points at, so they are stale: %s'
             % (len(orphans), orphans[:3]))


def config_classes_match(jars, problems):
    """The two loader copies of the annotated Cloth class must not drift apart.

    common compiles against a bare vanilla jar with no loader dependencies, so it cannot
    see Cloth and the annotated class has to exist once per loader module. Nothing makes
    an edit to one reach the other, and a config screen that offers different options on
    different loaders is exactly the kind of fault nobody notices until a player reports
    it. The copies are byte-identical source, so they compile to byte-identical classes.
    """
    # COMPARE THE SOURCE, NOT THE COMPILED CLASS. The 26.x branch compared class bytes
    # and got away with it because both loaders compiled the same way. With three modules
    # the same source produces class files of identical SIZE whose constant pools are
    # ORDERED differently, because each compiles against a different Minecraft mapping and
    # a different cloth artifact. That is a javac artifact, not drift, and comparing the
    # source is the more direct statement of the invariant anyway.
    #
    # ClothCompat is duplicated for the same reason and carries the same risk: it is the
    # only class allowed to touch Cloth, so a guard fixed on one loader and not the other
    # is exactly the drift this check exists for.
    for name in ('TravelerClothConfig.java', 'ClothCompat.java'):
        seen = {}
        for loader in jars:
            src = os.path.join(ROOT, loader, 'src', 'main', 'java', 'com', 'chillpavz',
                               'wanderingtraveler', 'config', name)
            if os.path.exists(src):
                seen[loader] = open(src, 'rb').read()
        if len(seen) != len(jars):
            problems.append('%s is missing from %s' % (
                name, ', '.join(sorted(set(jars) - set(seen)))))
        elif len(seen) > 1 and len(set(seen.values())) != 1:
            problems.append('the %s copies of %s have drifted apart; they must stay '
                            'identical' % (', '.join(sorted(seen)), name))
    for loader, path in jars.items():
        with zipfile.ZipFile(path) as zf:
            if CONFIG_CLASS not in zf.namelist():
                problems.append('%s ships no compiled TravelerClothConfig' % loader)


def config_annotations_match(problems):
    """Check each option's Cloth annotations against the lang file.

    THE ONE SOURCE-LEVEL CHECK IN THIS FILE, and it is here because the alternative is
    parsing RuntimeVisibleAnnotations out of the class file for the same answers. Reading
    fabric's copy alone is enough: the two are asserted byte-identical above.

    Three faults, all of which are silent and all of which have been seen:
      * a @Tooltip(count = N) with fewer than N lines in the lang file. Cloth does NOT
        probe for the keys - DefaultGuiTransformers builds exactly `count` components -
        so the missing ones render as raw translation keys.
      * a @PrefixText with no line to show.
      * a tooltip on a SLIDER. Cloth draws tooltips at the cursor, which on a slider is
        exactly over the value you are dragging to; the owner could not read the numbers
        they were trying to pick. Sliders carry their unit in the label instead.
    """
    src = os.path.join(ROOT, 'fabric', 'src', 'main', 'java', 'com', 'chillpavz',
                       'wanderingtraveler', 'config', 'TravelerClothConfig.java')
    lang = os.path.join(ROOT, '..', 'shared', 'resources', 'assets', MOD_ID,
                        'lang', 'en_us.json')
    if not (os.path.isfile(src) and os.path.isfile(lang)):
        problems.append('the config class or the lang file is missing from the source tree')
        return
    with open(lang, encoding='utf-8') as f:
        keys = json.load(f)
    text = open(src, encoding='utf-8').read()

    # An annotation block followed by the field it decorates. Anchored on `public`, so a
    # private field in the nested plumbing class is not mistaken for an option.
    pattern = re.compile(r'((?:[ \t]*@ConfigEntry[^\n]*\n)+)[ \t]*public [\w.<>]+ (\w+)')
    seen = 0
    for match in pattern.finditer(text):
        block, name = match.group(1), match.group(2)
        key = '%s.option.%s' % (I18N, name)
        seen += 1

        category = re.search(r'@ConfigEntry\.Category\("([^"]+)"\)', block)
        if category is None:
            problems.append('the option %r declares no @Category, so it lands in an '
                            'untranslated "default" tab' % name)
        else:
            # A tab name is as visible as an option name and fails the same silent way.
            tab = '%s.category.%s' % (I18N, category.group(1))
            if tab not in keys:
                problems.append('the category %r has no %s, so its tab is labelled with '
                                'that raw key' % (category.group(1), tab))

        # A tooltip on a slider IS allowed again. Cloth draws it at the cursor, over the
        # value you are dragging to, which is why they were stripped for one round; the
        # answer the owner preferred is the "Show Tooltips" option that hides all of them
        # at once, so the layout stays compact and the explanations stay available.
        tooltip = re.search(r'@ConfigEntry\.Gui\.Tooltip(?:\(count = (\d+)\))?', block)
        if tooltip:
            count = int(tooltip.group(1) or 1)
            if count == 1:
                have = 1 if key + '.@Tooltip' in keys else 0
            else:
                have = 0
                while '%s.@Tooltip[%d]' % (key, have) in keys:
                    have += 1
            if have != count:
                problems.append('%s asks for %d tooltip lines and the lang file has %d, '
                                'so the missing ones render as raw keys' % (key, count, have))
        if '@ConfigEntry.Gui.PrefixText' in block and key + '.@PrefixText' not in keys:
            problems.append('%s declares a prefix line and the lang file has none, so it '
                            'renders as a raw key above the option' % key)

    if not seen:
        problems.append('no annotated options were found in %s; the check has stopped '
                        'matching the source' % os.path.basename(src))


def main():
    problems = []
    found = 0
    jars = {}
    # BOTH LOADERS ARE EXPECTED HERE, unlike the 1.21.6 branch where Forge has no HUD
    # overlay API and the module is deliberately excluded. A missing jar must be a
    # failure rather than a quietly shorter run: with legacyForge, `reobfJar` is the task
    # that PRODUCES the Forge artifact, so anything that stops it leaves build/libs with a
    # sources jar and a javadoc jar and no mod at all - which reads as success if the
    # audit simply skips what it cannot find.
    for loader in sorted(ENTRYPOINTS):
        libs = os.path.join(ROOT, loader, 'build', 'libs')
        if not os.path.isdir(libs):
            problems.append('%s has not been built at all' % loader)
            continue
        before = found
        for name in sorted(os.listdir(libs)):
            if not name.endswith('.jar') or 'sources' in name or 'javadoc' in name:
                continue
            found += 1
            jars[loader] = os.path.join(libs, name)
            audit(jars[loader], loader, problems)
        if found == before:
            problems.append('%s built no mod jar; only its sources and javadoc jars are '
                            'in build/libs' % loader)

    config_classes_match(jars, problems)
    config_annotations_match(problems)

    print()
    if problems:
        print('%d PROBLEM(S):' % len(problems))
        for p in problems:
            print('   ' + p)
        sys.exit(1)
    print('%d jars audited, no problems.' % found)


if __name__ == '__main__':
    main()
