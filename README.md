# Wandering Traveler

An RPG-style title when you enter a new biome or dimension, drawn as hand made 3D artwork rather
than as plain text.

| | |
|---|---|
| Minecraft | 26.1, 26.1.1, 26.1.2 and 26.2, one jar per loader |
| Loaders | Fabric, NeoForge, Quilt (untested) |
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

## Dependencies

| | Fabric | NeoForge |
|---|---|---|
| Fabric API | required | |
| Cloth Config | required | required |
| Mod Menu | optional, adds the Config button | |

Neither is bundled. Cloth Config is LGPL-3.0 and stays a separate download for that reason;
NeoForge supplies its own Config button, so nothing like Mod Menu is needed there.

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

25 settings in six categories, reachable from the mods list on either loader, and stored in
`config/wanderingtraveler.json`.

| Category | What is in it |
|---|---|
| General | Master switch, artwork or plain text, dimension titles, tooltips |
| Appearance | Size per name length, and the colour and shadow of plain text |
| Placement | Nine screen positions, each already clear of the HUD, plus a nudge on each axis |
| Timing | Fade in, time on screen, fade out, and the minimum gap between two titles |
| When to Show | Elytra flight, and what to do with surface biomes while you are underground |
| Repeats | How often the same place is worth announcing, and a list that is never announced |

Two of these answer complaints that are worth spelling out. **Crossing several borders quickly**
shows the first title and the one you ended up in, rather than stamping each over the last. And
**while you are underground**, a biome that also exists at the surface can be hidden or shown as
plain text, so a cave stops announcing the meadow above your head. Cave biomes always show.

Changing a default only affects players who have no config file yet, because the file wins for every
setting it already contains. Delete it to pick up new defaults.

## Building

Requires JDK 25.

```
./gradlew build
```

Jars land in `fabric/build/libs` and `neoforge/build/libs`. Take the plain jar, not the `-sources` or
`-javadoc` one.

```
py -3 tools/audit_jar.py
```

Audits the built jars rather than the source tree, which is the only check that sees what the game
sees: every font definition and the texture it names, the config screen against its translations,
the metadata, and the icon.

## Project layout

| Path | What it holds |
|---|---|
| `common/` | Everything shared: title discovery, the tracker, timing, the renderer, the config holder |
| `fabric/`, `neoforge/` | Loader entry points and the Cloth Config data class |
| `tools/audit_jar.py` | The jar audit described above |

The Cloth Config class is duplicated across both loader modules on purpose and must stay identical.
It cannot live in `common`, because loader dependencies are not on its classpath.

## Credits

Built on [MultiLoader Template](https://github.com/jaredlll08/MultiLoader-Template) by jaredlll08.
