# SimpleTPA

SimpleTPA is a server-side only Fabric and NeoForge mod for Minecraft 26.3 that adds a player-to-player teleport request system.

Requests expire after 60 seconds. Accepting a request teleports the requester to the accepting player's position, and denying it expires the request immediately.

A request stays open, and an auto-accept waits for `/tpaccept`, while the accepting player is dead, in spectator mode, gliding with an elytra or watching the End credits, or while the requester could not arrive safely at their position: inside blocks, in or on a block the game counts as dangerous for players (fire, lava, lava cauldrons, magma, lit campfires, cactus, sweet berry bushes, wither roses, powder snow), or outside the world border. A request also stays open while the requester is dead or watching the End credits. The player who sent or accepted the request is told why.

## Features

Each player keeps an "auto-accept" whitelist of players who may teleport to them without their request being accepted; `/tpautoaccept` edits it. The `simpletpa:auto_accept` gamerule controls the feature and is enabled by default.

## Getting started

- Fabric Loader >=0.19.5 with Fabric API OR NeoForge >=26.3.0.99
- BrainageLib >=1.3.0
- Install on the server only; players don't need it on their clients.

## Commands

- `/simpletpa help` - prints this list.
- `/tpa <player>`, `/tprequest <player>` - request to teleport to a player.
- `/tpaccept [player]` - accept a specific player's request (`[player]` can be left blank if only one request is pending)
- `/tpdeny [player]` - deny a specific player's request (`[player]` can be left blank if only one request is pending)
- `/tpautoaccept list` - prints your "auto-accept" whitelist of players who can automatically teleport to you without their requests having to be accepted.
- `/tpautoaccept add <player>` - allow a specific player to be able to teleport to you immediately (if `simpletpa:auto_accept` is `true`)
- `/tpautoaccept remove <player>` - remove a player from your "auto-accept" whitelist.
- `/tpautoaccept clear` - clear your "auto-accept" whitelist.
- `/tpautoaccept *` - auto-accept requests from everyone (if `simpletpa:auto_accept` is `true`). Run it twice within 30 seconds to turn it on, and again to turn it off. `*` can be left out.
- `/tpauto` - short for `/tpautoaccept`, with all the same subcommands.
- `/gamerule simpletpa:auto_accept <true|false>` - when `true`, a player can teleport to another player instantly if that player has them on their "auto-accept" whitelist.

### BrainageLib commands

- `/servermods help` - prints the help lists for all mods which depend on BrainageLib.
- `/servermods config` - (operators only) prints the settings command for each mod which depends on BrainageLib (for SimpleTPA, the `/gamerule` command above).

## Configuration

The only setting is the `simpletpa:auto_accept` gamerule (default `true`), described under Commands.

## Compatibility

No known incompatibilities.

## Documentation

Everything is in this README.
