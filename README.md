# SimpleTPA

SimpleTPA is a server-side Fabric mod for Minecraft 26.2 that adds player-to-player teleport requests. Vanilla clients can join without installing the mod.

Requests expire after 60 seconds. Accepting a request teleports the requester to the accepting player's dimension, position, and rotation; denying it expires the request immediately.

Players can maintain a persistent auto-accept whitelist. The `simpletpa:allow_instant_tpa_accepting` gamerule controls whether listed players may teleport immediately and is enabled by default.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- BrainageLib 1.0.0 or newer
- Java 25 or newer

## Commands

- `/simpletpa help` — show the player command summary.
- `/tprequest <player>` — request to teleport to a player.
- `/tpaccept [player]` — accept the named player's request, or the only pending request.
- `/tpdeny [player]` — deny the named player's request, or the only pending request.
- `/tpautoaccept list` — list your auto-accept whitelist.
- `/tpautoaccept add <player>` — allow a player to teleport to you immediately while the gamerule permits it.
- `/tpautoaccept remove <player>` — remove a player from your auto-accept whitelist.
- `/tpautoaccept clear` — clear your auto-accept whitelist.

Operators can change automatic approval with `/gamerule simpletpa:allow_instant_tpa_accepting <true|false>`.

## Shared server help

SimpleTPA registers with BrainageLib's combined first-join notice. Players can run `/servermods help`; operators can also run `/servermods config`.
