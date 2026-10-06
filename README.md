# Wandering Traveler

An RPG-style title when you enter a new biome or dimension, drawn as hand made 3D artwork rather
than as plain text.

| | |
|---|---|
| Minecraft | 1.20 to 1.20.1, 1.21 to 1.21.11, and 26.1 to 26.3 |
| Loaders | Fabric, NeoForge, Forge, Quilt (untested) |
| Environment | Client only. A server neither needs it nor knows about it |
| Licence | All Rights Reserved. Modpacks may include an unmodified build without asking, see `LICENSE` |

An original mod. Not a fork or a port of any other project.

## What it ships

67 hand made titles: every vanilla Overworld, Nether and End biome, plus the Overworld, the Nether
and the End themselves. Dappled Forest is drawn ahead of its release.

**Every other biome still gets a title.** Anything without artwork is announced as plain text taken
from its own translation, so modded and datapack biomes work untouched and there is no such thing as
an unsupported biome. A name with no translation at all is humanised rather than shown as a raw key.

No mixins, at all. Nothing is injected into another mod's code or into a Minecraft method.

## Versions and loaders

| | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.1 to 26.3 | yes | yes | |
| 1.21.11 | yes | yes | yes |
| 1.21.6 to 1.21.10 | yes | yes | |
| 1.21.2 to 1.21.5 | yes | yes | |
| 1.21 and 1.21.1 | yes | yes | 1.21.1 only |
| 1.20 and 1.20.1 | yes | 1.20.1 | yes |

NeoForge at 1.20.1 is a fork of Forge 47 that still uses the `net.minecraftforge` packages, so it
runs the Forge build.

## Dependencies

| | Fabric | NeoForge | Forge |
|---|---|---|---|
| Fabric API | required | | |
| Cloth Config | required, optional at 1.20.x | required, optional at 1.20.1 | optional |
| Cloth Config Forge | | | optional, the Forge build of Cloth at 1.21.11 |
| Mod Menu | optional, adds the Config button | | |

Nothing is bundled. Cloth Config is LGPL-3.0 and stays a separate download for that reason.
NeoForge and Forge both supply their own Config button, so Mod Menu is only useful on Fabric.

**Without Cloth Config, where it is optional, the titles run on their default settings**, the
settings file is not written, and the Config button goes nowhere. Cloth is optional on every Forge
build because no single Cloth project covers them all: official Cloth Config ships Forge builds at
1.20.1 and 1.21.1, and at 1.21.11 the Forge build is the separate `cloth-config-forge` project, with
the same mod id. It is optional on everything at 1.20.x because Cloth publishes nothing tagged for
NeoForge there.

## Adding titles for other biomes

A resource pack can add a title for any biome or dimension, including one from another mod, with two
files and no code. Nothing needs to be registered and nothing needs asking for.

The mod lists its own font resources on every resource reload, so a title exists as soon as its font
file does. The path is derived from the biome id, which is why there is no table to collide over:

```
assets/wanderingtraveler/font/title/<namespace>/<path>.json
```

For `terralith:cave_forest` that is `font/title/terralith/cave_forest.json`. The file is an ordinary
font definition holding one or more `bitmap` providers, drawn left to right, each taking one
codepoint from U+E000 upward; a `space` provider at U+E010 closes the joins. The simplest way to see
the shape is to open one of the shipped files in the jar and point it at your own texture.

Two limits are worth knowing, because neither is reported anywhere when it is exceeded. A glyph
image may not be larger than 256 pixels in either direction, and every provider in one title must
declare the same `height`.

Fonts merge across resource packs rather than replacing each other, so your pack does not have to
contain ours and will not conflict with another pack that adds different biomes.

**Draw your own artwork.** Everything that ships with this mod is All Rights Reserved and may not be
copied, retextured or reused, but the mechanism above is yours to use and needs no permission.

## Configuration

27 settings in six categories, reachable from the mods list on every loader, and stored in
`config/wanderingtraveler.json`.

| Category | What is in it |
|---|---|
| General | Master switch, artwork or plain text, dimension titles, tooltips |
| Appearance | Size per name length, and the colour and shadow of plain text |
| Placement | Nine screen positions, each already clear of the HUD, plus a nudge on each axis |
| Timing | Fade in, time on screen, fade out, the minimum time on screen, how far into a biome a title waits for, and two pacing switches |
| When to Show | Elytra flight, and what to do with surface biomes while you are underground |
| Repeats | How many recent titles to remember, how soon the same name may show again, and a list that is never announced |

Two of these answer complaints that are worth spelling out. **Crossing several borders quickly**
shows the first title and the one you ended up in, rather than stamping each over the last. And
**while you are underground**, a biome that also exists at the surface can be hidden or shown as
plain text, so a cave stops announcing the meadow above your head. Cave biomes always show.

Changing a default only affects players who have no config file yet, because the file wins for every
setting it already contains. Delete it to pick up new defaults.

## Repository layout

One folder per band of Minecraft versions. Each is a complete Gradle build of its own, and every one
builds the same mod version.

| Folder | Jars it builds | Minecraft | Build with |
|---|---|---|---|
| `26.x` | Fabric, NeoForge | 26.1 to 26.3 | JDK 25 |
| `1.21.11` | NeoForge, Forge | 1.21.11 | JDK 24 |
| `1.21.6` | Fabric | 1.21.6 to 1.21.11 | JDK 24 |
| `1.21.6` | NeoForge | 1.21.6 to 1.21.10 | JDK 24 |
| `1.21` | Fabric, NeoForge | 1.21 to 1.21.5 | JDK 24 |
| `1.21` | Forge | 1.21.1 | JDK 24 |
| `1.20.1` | Fabric, Forge | 1.20 and 1.20.1 | JDK 22 |

A jar is named after the newest version it serves, so the folder name is the build input and not
the coverage. The JDK is what Gradle and its plugins need to run, not what the jar targets: the
classes are compiled for the Java version Mojang requires (17 at 1.20.x, 21 at 1.21.x, 25 at 26.x).

| Path | What it holds |
|---|---|
| `shared/java/` | Code that is identical in every band: title timing, the config holder, the platform plumbing |
| `shared/resources/` | The artwork, the fonts, the icon and the translations, identical in every band |
| `<band>/common/` | Shared code that differs by version: title discovery, the tracker, the renderer |
| `<band>/fabric/`, `neoforge/`, `forge/` | Loader entry points and the Cloth Config data class |
| `<band>/tools/audit_jar.py` | The jar audit described below |

Each band's `common` module compiles `shared/java` with its own sources and hands both, and
`shared/resources`, to its loaders, so a band folder builds only from inside this repository. The Cloth Config class is duplicated across a band's
loader modules on purpose and must stay identical; the audit checks that. It cannot live in `common`,
because loader dependencies are not on its classpath.

## Building

From inside a band folder:

```
./gradlew build
py -3 tools/audit_jar.py
```

Jars land in each loader's `build/libs`. Take the plain jar, not the `-sources` or `-javadoc` one.
The audit checks the built jars rather than the source tree, which is the only check that sees what
the game sees: every font definition and the texture it names, the config screen against its
translations, the metadata, the icon, the class-file target, and on 1.20.1 whether each jar carries
the member names its loader expects.

## Credits

Built on [MultiLoader Template](https://github.com/jaredlll08/MultiLoader-Template) by jaredlll08.
