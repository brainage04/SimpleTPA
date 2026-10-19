# SimpleTPA

SimpleTPA is a server-side only Fabric and NeoForge mod for Minecraft 26.2 that adds a player-to-player teleport request system.

Requests expire after 60 seconds. Accepting a request teleports the requester to the accepting player's position, and denying it expires the request immediately.

Players can control an "auto-accept" whitelist which specifies who is allowed to teleport to them without their requests having to be accepted using `/tpautoaccept`. This functionality is controlled by the `simpletpa:auto_accept` gamerule, and is enabled by default.

## Dependencies

- Fabric Loader >=0.19.3 with Fabric API OR NeoForge >=26.2.0.88
- BrainageLib >=1.0.1

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
- `/gamerule simpletpa:auto_accept <true|false>` - when set to `true`, allows players to be able to teleport to each other instantly, if the player they are teleporting to has them added to their "auto-accept" whitelist.

### BrainageLib commands

- `/servermods help` - prints the help lists for all mods which depend on BrainageLib.
- `/servermods config` - (operators only) prints the settings command for each mod which depends on BrainageLib (for SimpleTPA, the `/gamerule` command above).
