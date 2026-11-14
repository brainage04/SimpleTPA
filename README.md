# SimpleTPA

SimpleTPA is a server-side only Fabric and NeoForge mod for Minecraft 26.3 that adds a player-to-player teleport request system.

Requests expire after 60 seconds. Accepting a request teleports the requester to the accepting player's position, and denying it expires the request immediately.

A request stays open, and an auto-accept waits for `/tpaccept`, while the accepting player is dead, in spectator mode, in the air by flight or with an elytra (unless the requester can fly, for example in creative mode), or watching the End credits. A request also stays open while the requester is dead or watching the End credits.

A request also waits while the requester could not arrive safely at the accepting player's position. That is the case inside blocks, in or on a block the game counts as dangerous for players (fire, lava, lava cauldrons, magma, lit campfires, cactus, sweet berry bushes, wither roses, powder snow), outside the world border, and in the air above a drop that would hurt them. A player riding or standing on something you can stand on, such as a happy ghast with a harness, gives you a landing spot, so their request goes through, and the drop below the spot is what decides: a drop shorter than the game's safe fall distance (three blocks by default) is fine, so a player sneaking at the edge of a block over a short drop is still reachable. The check looks at most 128 blocks down, and a deeper drop counts as one that hurts, even for a requester whose safe fall distance is longer. A drop into water or powder snow, or through a cobweb, ladder, vines or another block the game counts as resetting a fall, does not hurt, but a fall that goes on below such a block counts again from about its bottom: from where the requester last was in a ladder or vines, and from where a fast fall's last tick began when it passes a thin layer of them or of water in one go. A fall faster than 8 blocks a tick, which takes a raised gravity or a lowered air drag, counts on through such a block, because the game may not notice it. The part of a fall that the requester's Slow Falling lasts through does not hurt either, as long as the fall passes no such block: those blocks slow the requester down, so the check cannot tell where the effect runs out, and below one it counts the whole fall. When the effect runs out on the way down, the check does not allow for network delay: the game can count up to about half a block more fall per tick (50 ms) of round trip, so a requester with a high ping may take some fall damage the check did not expect. All of these landing checks, and the flight check, are skipped for a creative or spectator requester. While the `fall_damage` game rule is off, no fall hurts, so the drop check refuses nothing.

The drop check also follows the blocks that change the damage of a landing on them: a drop of one or two blocks onto the tip of a stalagmite (pointed dripstone pointing up) hurts, and a landing on hay, a honey block or a slime block takes less damage or none. A drop that ends with your feet in powder snow counts as hurting too, unless you wear leather boots: the snow holds you, freezes you unless you wear leather, and only leather boots let you climb out. The check judges scaffolding and powder snow as you would meet them falling from the other player's position, wherever you stand now: scaffolding below that position holds you, scaffolding you would stand in there does not make the spot unsafe, and powder snow is taken to let you sink through it, even in leather boots.

Every player chooses for themselves whether the drop check holds their requests (`/tpasafety`). With it off, a request goes through and the requester falls. The player who sent or accepted the request is told why it waited.

## Features

Each player keeps an "auto-accept" whitelist of players who may teleport to them without their request being accepted; `/tpautoaccept` edits it. The `simpletpa:auto_accept` gamerule controls the feature and is enabled by default.

Landing checks stop a teleport that would drop the requester. Each player turns them off and on for their own requests with `/tpasafety off` and `/tpasafety on`; they are on for everyone to begin with.

## Getting started

- Fabric Loader >=0.19.5 with Fabric API OR NeoForge >=26.3.0.99
- BrainageLib >=2.0.0 and <3.0.0
- Install on the server only; players don't need it on their clients.

## Commands

- `/simpletpa help` – prints this list.
- `/tpa <player>`, `/tprequest <player>` – request to teleport to a player.
- `/tpaccept [player]` – accept a pending request from a player, by name or UUID (`[player]` can be left blank if only one request is pending).
- `/tpdeny [player]` – deny a pending request from a player, by name or UUID (`[player]` can be left blank if only one request is pending).
- `/tpautoaccept list` – prints your "auto-accept" whitelist of players who can automatically teleport to you without their requests having to be accepted. A name that two entries share is followed by each player's UUID.
- `/tpautoaccept add <player>` – allow a specific player to be able to teleport to you immediately (if `simpletpa:auto_accept` is `true`)
- `/tpautoaccept remove <player>` – remove a player from your "auto-accept" whitelist, whether they are online or not. `<player>` is an online player's name, a name from `/tpautoaccept list` or the player's UUID. A name that two offline entries share removes neither of them, so remove them by UUID.
- `/tpautoaccept clear` – clear your "auto-accept" whitelist.
- `/tpautoaccept *` – auto-accept requests from everyone (if `simpletpa:auto_accept` is `true`). Run it twice within 30 seconds to turn it on, and again to turn it off. `*` can be left out.
- `/tpauto` – short for `/tpautoaccept`, with all the same subcommands.
- `/tpasafety` – prints whether your TP requests wait while the player you teleport to is in the air above a long drop.
- `/tpasafety on` – your requests wait in that case and you are told why.
- `/tpasafety off` – your requests go through in that case, and you fall when you arrive.
- `/gamerule simpletpa:auto_accept <true|false>` – when `true`, a player can teleport to another player instantly if that player has them on their "auto-accept" whitelist.

### BrainageLib commands

- `/servermods help` – prints the help lists for all mods which depend on BrainageLib.
- `/servermods config` – (operators only) prints the settings command for each mod which depends on BrainageLib (for SimpleTPA, the `/gamerule` command above).

## Configuration

The `simpletpa:auto_accept` gamerule (default `true`) is described under Commands. SimpleTPA 1.1.0 and earlier called it `simpletpa:allow_instant_tpa_accepting`. A world that set the old name keeps its value the first time it starts with a later version.

Each player's choice of the landing check is kept in `simpletpa-safety.json` in the world folder, and each player's auto-accept whitelist in `simpletpa-instant-accept-whitelist.json`. A file the mod cannot read is left as it is, and the choices apply until the server stops.

## Compatibility

No known incompatibilities.

## Documentation

Everything is in this README.
