# Wandering Traveler

An RPG-style title when you enter a new biome or dimension, drawn as hand made 3D artwork rather
than as plain text.

An original mod. Not a fork or a port of any other project. All Rights Reserved, see `LICENSE`.

## Layout

One folder per band of Minecraft versions. Each folder is a complete Gradle build of its own, with
its own `README.md`, and every folder builds the same mod version from the same shared design.

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
the coverage. The Forge jar in `1.20.1` also runs on NeoForge, which at 1.20.1 is a fork of Forge.

Releases are assembled from these jars, one file per loader.

## Building

From inside a band folder:

```
./gradlew build
py -3 tools/audit_jar.py
```

Jars land in each loader's `build/libs`. The audit checks the built jars rather than the source tree.
