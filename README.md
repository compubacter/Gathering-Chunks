# Gathering Chunks (Fabric 1.21.11 Port)

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen.svg)](https://www.minecraft.net/)
[![Mod Loader](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Gathering Chunks is a survival-progression mod where the world expands one chunk at a time. Start small, gather resources, craft chunk tools, and grow your world safely.

This repository is based on Ryvione's maintained fork of the original Chunk By Chunk mod and includes a strict Fabric port targeting Minecraft 1.21.11.

## Compatibility

- Minecraft: 1.21.11
- Loader: Fabric
- Java: 21 required

## Gameplay Overview

- Start from a constrained world state and expand into the void.
- Craft and use chunk spawners to generate neighboring chunks.
- Progress through World Fragment -> World Shard -> World Crystal -> World Core.
- Use utility blocks to progress:
- World Forge
- World Scanner
- World Mender
- Bedrock Chest progression support

## Port Status (1.21.11)

This port focuses on compatibility and stability while preserving the original item/mechanic loop:

- Updated to strict Minecraft/Fabric 1.21.11 toolchain.
- Updated world/block entity serialization and saved-data paths for modern APIs.
- Updated registry/holder access and command/dimension transfer APIs.
- Updated datapack resources (recipes/advancements/tags) for 1.21.11 loading.
- Fixed scanner data reload timing to avoid registry-context issues.
- Fixed inventory/held item rendering for mod items.
- Fixed right-click interaction regressions on forge/scanner/mender blocks.
- Added starter guide book onboarding grant with safer one-time delivery behavior.

## Build From Source

```bash
git clone https://github.com/compubacter/Gathering-Chunks.git
cd Gathering-Chunks
./gradlew :Fabric:build
```

Built jar output:

- `Fabric/build/libs/ChunkByChunk-fabric-1.21.11-2.3.0.jar`

## Dedicated Server Notes

- Use Java 21.
- Place the built mod jar in your Fabric server `mods` folder.
- Include the matching Fabric API jar for Minecraft 1.21.11.

## Commands

The current server command set includes:

- `/spawnChunk`
- `/spawnRandomChunk`
- `/chests`

Permissions and behavior follow vanilla/Fabric server operator levels.

## Contributing

Contributions are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Commit your changes with clear messages.
4. Push your branch to your fork.
5. Open a Pull Request.

For large changes, open an issue first to discuss approach and scope.

## Credits

- Original mod author: immortius
- Fork maintainer: Ryvione
- Ryvione fork repository: https://github.com/ryvione/Gathering-Chunks
- Original repository: https://github.com/immortius/chunkbychunk

## License

MIT. See `LICENSE` for details.

Original work Copyright (c) immortius  
Modified work Copyright (c) 2026 Ryvione
