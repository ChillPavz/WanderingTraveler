"""Audit the BUILT JARS, which is the only check that sees what the game sees.

Every fault this catches has already shipped once. The titles were bitmap fonts
until Oct 2026 and failed in game more than once with nothing in the log (a namespaced
provider type, a font id with a second "font/", fractional seams between strips). They
are single PNGs now, drawn as GUI textures, and the checks below assert what the mod
needs from them: the path shape it derives the biome id from, a real PNG, a size every
graphics card can upload, and no file left over from the font route.

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

# A GUI texture larger than the graphics card's maximum texture size fails to upload.
# 8192 is supported by every card that runs these versions; the generator keeps under it.
MAX_TEXTURE_PX = 8192

# A title is drawn 12 GUI units tall times the artwork scale; under 12 px it is magnified
# even at GUI scale 1, which only happens if an export went badly wrong.
MIN_IMAGE_HEIGHT = 12

# Cloth's own i18n scheme, read out of ConfigScreenProvider in cloth-config 26.2.155:
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

ENTRYPOINTS = {
    'fabric': 'com.chillpavz.wanderingtraveler.WanderingTravelerFabric',
    'neoforge': 'com.chillpavz.wanderingtraveler.WanderingTravelerNeoForge',
}


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


# REFERENCES THAT DO NOT SPAN THE 26.x LINE. Each resolves on some version this jar
# declares and not on another, so a jar carrying one installs cleanly and then throws
# NoSuchMethodError, NoSuchFieldError or NoClassDefFoundError on the version that lacks it.
# All five were found by resolving the built jars against the real 26.1, 26.2 and 26.3
# artefacts; the first two were in this mod.
#
# Matched as MEMBER REFERENCES - owner, name and descriptor out of the constant pool - and
# never as bare strings, because the replacement for blocksMotion is a private helper of
# the same name, and PackResources.listResources is the replacement for the other one.
NON_SPANNING = [
    ('Methodref', 'net/minecraft/world/level/block/state/BlockState', 'blocksMotion', None,
     'gone at 26.3 with no renamed twin; write out isSolid() minus cobweb and bamboo sapling'),
    ('InterfaceMethodref', 'net/minecraft/server/packs/resources/ResourceManager',
     'listResources', '(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;',
     'takes a ResourceManager.Selector from 26.3; list the packs instead'),
    ('InterfaceMethodref', 'net/minecraft/server/packs/resources/ResourceManager',
     'listResourceStacks', '(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;',
     'takes a ResourceManager.Selector from 26.3'),
    ('Class', 'com/mojang/blaze3d/pipeline/RenderPipeline', None, None,
     'moved to com/mojang/renderpearl/api/pipeline at 26.3; use the pipeline-free blit'),
    ('Fieldref', 'com/mojang/blaze3d/platform/InputConstants$Type', 'KEYSYM', None,
     'merged into KEYBOARD at 26.3; use the KeyMapping constructor that takes no Type'),
]

_CP_REF = {9: 'Fieldref', 10: 'Methodref', 11: 'InterfaceMethodref'}


def member_refs(data):
    """Every Class and member reference a class file makes: (kind, owner, name, desc)."""
    count = struct.unpack('>H', data[8:10])[0]
    pos, i, entries = 10, 1, {}
    while i < count:
        tag = data[pos]
        if tag == 1:
            n = struct.unpack('>H', data[pos + 1:pos + 3])[0]
            entries[i] = (1, data[pos + 3:pos + 3 + n].decode('utf-8', 'replace'))
            pos += 3 + n
        elif tag in (5, 6):                   # long and double take two slots
            pos += 9
            i += 1
        elif tag == 7:
            entries[i] = (7, struct.unpack('>H', data[pos + 1:pos + 3])[0])
            pos += 3
        elif tag == 12:
            entries[i] = (12, struct.unpack('>HH', data[pos + 1:pos + 5]))
            pos += 5
        elif tag in _CP_REF:
            entries[i] = (tag, struct.unpack('>HH', data[pos + 1:pos + 5]))
            pos += 5
        else:
            pos += 1 + _CP_SKIP[tag]
        i += 1

    def utf(k):
        return entries[k][1]

    out = set()
    for tag, val in entries.values():
        if tag == 7:
            out.add(('Class', utf(val), None, None))
        elif tag in _CP_REF:
            owner = utf(entries[val[0]][1])
            name_i, desc_i = entries[val[1]][1]
            out.add((_CP_REF[tag], owner, utf(name_i), utf(desc_i)))
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
    else:
        toml = zf.read('META-INF/neoforge.mods.toml').decode()
        for line in toml.splitlines():
            if '${' in line:
                fail('neoforge.mods.toml has an unexpanded placeholder: %s' % line.strip())
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

    if 'pack.mcmeta' not in names:
        fail('pack.mcmeta is missing')
    else:
        pack = json.loads(zf.read('pack.mcmeta'))['pack']
        for field in ('pack_format', 'supported_formats', 'min_format', 'max_format'):
            if field not in pack:
                fail('pack.mcmeta is missing %s; 26.x needs all four' % field)

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

    # --- nothing that breaks on one of the versions this jar declares -----
    ours = sorted(n for n in names
                  if n.startswith('com/chillpavz/%s/' % MOD_ID) and n.endswith('.class'))
    if not ours:
        fail("none of the mod's own classes are in the jar")
    refs = set()
    for name in ours:
        refs |= {(name,) + r for r in member_refs(zf.read(name))}
    if not any(r[1:] == ('Class', 'net/minecraft/client/Minecraft', None, None) for r in refs):
        # A parser that silently reads nothing would pass every check below.
        fail('the constant-pool reader found no reference to net/minecraft/client/Minecraft, '
             'so it is not reading the classes and the span check below proves nothing')
    for kind, owner, member, desc, why in NON_SPANNING:
        for cls, k, o, n, d in sorted(refs):
            if k == kind and o == owner and member in (None, n) and desc in (None, d):
                fail('%s references %s.%s%s, which does not exist on every version this jar '
                     'declares: %s' % (cls[:-len('.class')].replace('/', '.'), owner,
                                       n or '', d or '', why))

    # --- no mixins, on purpose -------------------------------------------
    stray = [n for n in names if n.endswith('mixins.json')]
    if stray:
        fail('mixin configs are declared but this mod ships none: %s' % stray)

    # --- the titles -------------------------------------------------------
    # One PNG per title at textures/title/<namespace>/<name>.png, drawn as a GUI texture.
    # The mod maps that path to the biome id <namespace>:<name>, so the shape is what has
    # to hold; a font left over from the old strip route would ship dead weight.
    prefix = 'assets/%s/textures/title/' % MOD_ID
    images = sorted(n for n in names if n.startswith(prefix) and not n.endswith('/'))
    if not images:
        fail('no title images in the jar at all')
    leftovers = sorted(n for n in names
                       if n.startswith('assets/%s/font/' % MOD_ID) and not n.endswith('/'))
    if leftovers:
        fail('%d file(s) from the old font route are still in the jar: %s'
             % (len(leftovers), leftovers[:3]))
    largest = 0
    for image in images:
        parts = image[len(prefix):].split('/')
        if len(parts) != 2 or not parts[1].endswith('.png') or len(parts[1]) <= 4:
            fail('%s is not textures/title/<namespace>/<name>.png, so the mod cannot '
                 'derive a biome id from it' % image)
            continue
        size = png_size(zf.read(image))
        if size is None:
            fail('%s is not a PNG' % image)
            continue
        if max(size) > MAX_TEXTURE_PX:
            fail('%s is %dx%d, over the %d px texture ceiling'
                 % (image, size[0], size[1], MAX_TEXTURE_PX))
        if size[1] < MIN_IMAGE_HEIGHT:
            fail('%s is only %d px tall, so it is magnified on every screen'
                 % (image, size[1]))
        largest = max(largest, max(size))
    print('   %-10s %3d title images, largest side %d px' % (loader, len(images), largest))


def config_classes_match(jars, problems):
    """The two loader copies of the annotated Cloth class must not drift apart.

    common compiles against a bare vanilla jar with no loader dependencies, so it cannot
    see Cloth and the annotated class has to exist once per loader module. Nothing makes
    an edit to one reach the other, and a config screen that offers different options on
    different loaders is exactly the kind of fault nobody notices until a player reports
    it. The copies are byte-identical source, so they compile to byte-identical classes.
    """
    seen = {}
    for loader, path in jars.items():
        with zipfile.ZipFile(path) as zf:
            if CONFIG_CLASS in zf.namelist():
                seen[loader] = zf.read(CONFIG_CLASS)
    if len(seen) == 2 and len(set(seen.values())) != 1:
        problems.append('the fabric and neoforge copies of TravelerClothConfig have '
                        'drifted apart; they must stay byte-identical')


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
    for loader in ('fabric', 'neoforge'):
        libs = os.path.join(ROOT, loader, 'build', 'libs')
        if not os.path.isdir(libs):
            problems.append('%s has not been built' % loader)
            continue
        for name in sorted(os.listdir(libs)):
            if not name.endswith('.jar') or 'sources' in name or 'javadoc' in name:
                continue
            found += 1
            jars[loader] = os.path.join(libs, name)
            audit(jars[loader], loader, problems)

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
