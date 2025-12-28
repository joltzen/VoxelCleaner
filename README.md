# VoxelCleaner

**VoxelCleaner** is a Minecraft mod designed to provide powerful voxel-based editing capabilities via in-game commands. With features like room generation, area hollowing, history tracking (undo/redo), loot management, and block protection, it is an essential tool for creators, map builders, and server admins.

---

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Configuration](#configuration)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)
- [Dependencies](#dependencies)
- [Contributors](#contributors)
- [License](#license)

---

## Features

- 🧱 **Room Generation**: Generate rooms of specified width, height, and depth with a single command.
- 🕳️ **Area Hollowing**: Clear out volumes while preserving outer shell blocks.
- ♻️ **Undo/Redo**: Revert or reapply world changes with history tracking.
- 🎁 **Loot Management**: Break blocks, spawn chests, and auto-distribute loot.
- 🔒 **Block Protection**: Prevents modification of protected blocks like spawners and blocks with entities.
- 🧰 **Command Utilities**: Utility methods to assist with player and block targeting in commands.
- 👁️ **Live Preview**: Particle previews before executing destructive operations

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft.
2. Install the [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api).
3. Download the `VoxelCleaner` mod JAR and place it in your `mods` folder.
4. Launch the game using the Fabric profile.

---

## Usage

### Main Commands

#### `/voxelcleaner`
**Alias:** `/vc`  
Clears (hollows) a rectangular area in front of the player.

```bash
/voxelcleaner <width> <height> <depth>
/voxelcleaner <width> <height> <depth> <material>
/voxelcleaner <width> <height> <depth> <material> <force>
/voxelcleaner <width> <height> <depth> <material> <force> <loot>
```

**Parameters**:
- `width` – Inner width of the area
- `height` – Inner height of the area
- `depth` – Inner depth of the area
- `material` *(optional)* – Block used for the outer shell
- `force` *(optional, boolean)* – Removes protected blocks
- `loot` *(optional, boolean)* – Collects drops into chests

---

#### `/voxelroom`
**Alias:** `/vr`  
Creates a fully built room with walls, floor, and ceiling.

```bash
/voxelroom <width> <height> <depth> <walls> <floor> <ceiling>
/voxelroom <width> <height> <depth> <walls> <floor> <ceiling> <force>
/voxelroom <width> <height> <depth> <walls> <floor> <ceiling> <force> <loot>
```

**Parameters**:
- `width` – Inner width of the room
- `height` – Inner height of the room
- `depth` – Inner depth of the room
- `walls` – Wall block
- `floor` – Floor block
- `ceiling` – Ceiling block
- `force` *(optional, boolean)* – Overrides protected blocks
- `loot` *(optional, boolean)* – Collects removed blocks into chests

---

### History Commands

#### `/voxelundo`
**Alias:** `/vcu`  
Undoes the last voxel action.

```bash
/voxelundo
/voxelundo <count>
```

#### `/voxelredo`
**Alias:** `/vcr`  
Redoes previously undone voxel actions.

```bash
/voxelredo
/voxelredo <count>
```

#### `/voxelhistory`
**Alias:** `/vch`  
Displays the command history for the current player.

```bash
/voxelhistory
/voxelhistory <count>
```

---

### Notes

- All commands are **player-only** (cannot be executed from console).
- Undo/redo works **per player** and **per dimension**.
- `force` and `loot` default to `false` if not specified.
- Protected blocks (block entities, spawners) are preserved unless `force = true`.


## Configuration

All constants are centralized in `VoxelConfig.java`. Notable configuration options:

```java
public static final int MAX_W = 64;
public static final int MAX_H = 64;
public static final int MAX_D = 64;
public static final int MAX_ACTIONS_PER_PLAYER = 10;
public static final int MAX_HISTORY_LINES = 20;
public static final String TIMEZONE = "America/New_York";
```

Adjust these values to fine-tune player limits, history depth, and size constraints.

---

## Examples

```bash
# Hollow out a 10x5x10 area using default material
/voxelcleaner 10 5 10

# Hollow out a 10x5x10 area with stone shell and collect loot
/voxelcleaner 10 5 10 minecraft:stone true true

# Create a 7x4x7 room using different blocks for walls, floor, and ceiling
/voxelroom 7 4 7 minecraft:oak_planks minecraft:birch_planks minecraft:glass

# Create a 7x4x7 room and override protected blocks
/voxelroom 7 4 7 minecraft:oak_planks minecraft:birch_planks minecraft:glass true

# Undo the last 2 voxel actions
/voxelundo 2

# Redo the last undone voxel action
/voxelredo 1

# View the last 5 voxel actions for the current player
/voxelhistory 5
```

---

## Troubleshooting

- ❗ **Commands not recognized?** Make sure the mod is loaded and you're using the Fabric profile.
- ❗ **Block edits not working?** Ensure you're not targeting protected blocks (e.g., spawners, blocks with entities).
- ❗ **Undo stack not working?** Check `VoxelConfig.MAX_ACTIONS_PER_PLAYER` to ensure history is enabled for players.

---

## Dependencies

- [Minecraft (Fabric)](https://fabricmc.net/)
- [Fabric API](https://github.com/FabricMC/fabric)
- Brigadier (Minecraft’s command parser)
- Minecraft Server Classes (for block, entity, world management)

---

## Contributors

- **Jason Oltzen** – Author and core developer

---

## License

This work is released under the **Creative Commons CC0 1.0 Universal (CC0 1.0) Public Domain Dedication**.
# VoxelCleaner

**VoxelCleaner** is a Minecraft mod designed to provide powerful voxel-based editing capabilities via in-game commands. With features like room generation, area hollowing, history tracking (undo/redo), loot management, and block protection, it is an essential tool for creators, map builders, and server admins.

---

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Configuration](#configuration)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)
- [Dependencies](#dependencies)
- [Contributors](#contributors)
- [License](#license)

---

## Features

- 🧱 **Room Generation**: Generate rooms of specified width, height, and depth with a single command.
- 🕳️ **Area Hollowing**: Clear out volumes while preserving outer shell blocks.
- ♻️ **Undo/Redo**: Revert or reapply world changes with history tracking.
- 🎁 **Loot Management**: Break blocks, spawn chests, and auto-distribute loot.
- 🔒 **Block Protection**: Prevents modification of protected blocks like spawners and blocks with entities.
- 🧰 **Command Utilities**: Utility methods to assist with player and block targeting in commands.
- 👁️ **Live Preview**: Particle previews before executing destructive operations

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft.
2. Install the [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api).
3. Download the `VoxelCleaner` mod JAR and place it in your `mods` folder.
4. Launch the game using the Fabric profile.

---

## Usage

### Main Commands

#### `/voxelcleaner`
**Alias:** `/vc`  
Clears (hollows) a rectangular area in front of the player.

```bash
/voxelcleaner <width> <height> <depth>
/voxelcleaner <width> <height> <depth> <material>
/voxelcleaner <width> <height> <depth> <material> <force>
/voxelcleaner <width> <height> <depth> <material> <force> <loot>
```

**Parameters**:
- `width` – Inner width of the area
- `height` – Inner height of the area
- `depth` – Inner depth of the area
- `material` *(optional)* – Block used for the outer shell
- `force` *(optional, boolean)* – Removes protected blocks
- `loot` *(optional, boolean)* – Collects drops into chests

---

#### `/voxelroom`
**Alias:** `/vr`  
Creates a fully built room with walls, floor, and ceiling.

```bash
/voxelroom <width> <height> <depth> <walls> <floor> <ceiling>
/voxelroom <width> <height> <depth> <walls> <floor> <ceiling> <force>
/voxelroom <width> <height> <depth> <walls> <floor> <ceiling> <force> <loot>
```

**Parameters**:
- `width` – Inner width of the room
- `height` – Inner height of the room
- `depth` – Inner depth of the room
- `walls` – Wall block
- `floor` – Floor block
- `ceiling` – Ceiling block
- `force` *(optional, boolean)* – Overrides protected blocks
- `loot` *(optional, boolean)* – Collects removed blocks into chests

---

### History Commands

#### `/voxelundo`
**Alias:** `/vcu`  
Undoes the last voxel action.

```bash
/voxelundo
/voxelundo <count>
```

#### `/voxelredo`
**Alias:** `/vcr`  
Redoes previously undone voxel actions.

```bash
/voxelredo
/voxelredo <count>
```

#### `/voxelhistory`
**Alias:** `/vch`  
Displays the command history for the current player.

```bash
/voxelhistory
/voxelhistory <count>
```

---

### Notes

- All commands are **player-only** (cannot be executed from console).
- Undo/redo works **per player** and **per dimension**.
- `force` and `loot` default to `false` if not specified.
- Protected blocks (block entities, spawners) are preserved unless `force = true`.


## Configuration

All constants are centralized in `VoxelConfig.java`. Notable configuration options:

```java
public static final int MAX_W = 64;
public static final int MAX_H = 64;
public static final int MAX_D = 64;
public static final int MAX_ACTIONS_PER_PLAYER = 10;
public static final int MAX_HISTORY_LINES = 20;
public static final String TIMEZONE = "America/New_York";
```

Adjust these values to fine-tune player limits, history depth, and size constraints.

---

## Examples

```bash
# Hollow out a 10x5x10 area using default material
/voxelcleaner 10 5 10

# Hollow out a 10x5x10 area with stone shell and collect loot
/voxelcleaner 10 5 10 minecraft:stone true true

# Create a 7x4x7 room using different blocks for walls, floor, and ceiling
/voxelroom 7 4 7 minecraft:oak_planks minecraft:birch_planks minecraft:glass

# Create a 7x4x7 room and override protected blocks
/voxelroom 7 4 7 minecraft:oak_planks minecraft:birch_planks minecraft:glass true

# Undo the last 2 voxel actions
/voxelundo 2

# Redo the last undone voxel action
/voxelredo 1

# View the last 5 voxel actions for the current player
/voxelhistory 5
```

---

## Troubleshooting

- ❗ **Commands not recognized?** Make sure the mod is loaded and you're using the Fabric profile.
- ❗ **Block edits not working?** Ensure you're not targeting protected blocks (e.g., spawners, blocks with entities).
- ❗ **Undo stack not working?** Check `VoxelConfig.MAX_ACTIONS_PER_PLAYER` to ensure history is enabled for players.

---

## Dependencies

- [Minecraft (Fabric)](https://fabricmc.net/)
- [Fabric API](https://github.com/FabricMC/fabric)
- Brigadier (Minecraft’s command parser)
- Minecraft Server Classes (for block, entity, world management)

---

## Contributors

- **Jason Oltzen** – Author and core developer

---

## License

This work is released under the **Creative Commons CC0 1.0 Universal (CC0 1.0) Public Domain Dedication**.
