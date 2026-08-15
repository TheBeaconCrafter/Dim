<div align="center">
 <h1>Dim</h1>
 <p>Libre Minecraft anticheat fork with simulation-based movement checks.</p>
</div>

Dim is a maintained fork of [GrimAC](https://github.com/GrimAnticheat/Grim), an open-source Minecraft anticheat designed to support current and legacy Minecraft versions. Dim keeps the upstream implementation and API package names where practical so that upstream changes can be merged with minimal conflict.

## Attribution and license

Dim is a modified version of GrimAC by DefineOutside and its contributors. The original project and all existing copyright and license notices remain attributable to their respective authors. Dim is distributed under the [GNU General Public License, version 3](LICENSE); modified source and releases must continue to comply with that license.

See [ATTRIBUTIONS.md](ATTRIBUTIONS.md) for the complete fork attribution and [UPSTREAM.md](UPSTREAM.md) for the update workflow.

## Features

- Simulation-based movement anticheat with latency compensation.
- World and inventory replication for packet-level checks.
- Bukkit, Spigot, Paper, Folia, Purpur, and Fabric support from the same source tree.
- Velocity support through the optional BeaconLabsVelocity original-client-protocol bridge.

## Installation

- Java 17 or higher is required at runtime.
- Install the generated `dim-bukkit-*.jar` on Bukkit, Spigot, Paper, Folia, or Purpur.
- Install the appropriate `dim-fabric-*.jar` on Fabric.
- For a Velocity network using ViaVersion, install the BeaconLabsVelocity bridge on the proxy and Dim on every backend. Keep ViaVersion on the proxy; the bridge sends the original client protocol over `beaconlabs:protocol_version`.

The primary administrative command is `/dim`. `/grim` and `/grimac` remain compatibility aliases for existing server automation. Internal `ac.grim.grimac` API/package names and legacy `grim.*` permission nodes are also retained intentionally for compatibility and easier upstream synchronization.

## Building

```bash
./gradlew build
```

Bukkit and Fabric artifacts are written to their respective `build/libs` directories. The default shaded Bukkit artifact is compatible with Bukkit, Spigot, and Paper. Use `./gradlew :bukkit:build -PshadePE=false` when PacketEvents is supplied separately.

## Upstream project

- Original source: [GrimAnticheat/Grim](https://github.com/GrimAnticheat/Grim)
- Original plugin API: [GrimAnticheat/GrimAPI](https://github.com/GrimAnticheat/GrimAPI)
- Dim source: [TheBeaconCrafter/Dim](https://github.com/TheBeaconCrafter/Dim)

Dim-specific changes are kept in product metadata, proxy compatibility, and fork documentation rather than being mixed into a package-wide rename. This is deliberate: it keeps future upstream updates reviewable and reduces merge conflicts.
