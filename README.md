# SimpleTPA

SimpleTPA is a server-side Fabric and NeoForge mod for Minecraft 26.2 that adds player-to-player teleport requests. Vanilla clients can join without installing the mod.

Requests expire after 60 seconds. Accepting a request teleports the requester to the accepting player's dimension, position, and rotation; denying it expires the request immediately.

Players can maintain a persistent auto-accept whitelist. The `simpletpa:allow_instant_tpa_accepting` gamerule controls whether listed players may teleport immediately and is enabled by default.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer with Fabric API, or NeoForge 26.2.0.23-beta or newer
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

## Migrating from the Fabric-only release

Install exactly one SimpleTPA JAR: the Fabric JAR on Fabric, or the `-neoforge` JAR on NeoForge. Remove the old SimpleTPA JAR before switching loaders. SimpleTPA remains server-side, so vanilla clients do not need it; install the matching loader-specific BrainageLib dependency on the server. The stable `simpletpa` mod ID and its world-stored auto-accept data path are unchanged. Building the repository root produces both loader artifacts under `build/libs`.

## Shared server help

SimpleTPA registers with BrainageLib's combined first-join notice. Players can run `/servermods help`; operators can also run `/servermods config`.

## Verification

`./gradlew runAllProductionGameTests` runs the Fabric and NeoForge server suites. The Fabric suite uses independently controlled Carpet fake players for request, accept, deny, and auto-accept behavior.

`./gradlew runClientGameTest` launches a visual accept/deny/auto-accept scenario with the connected client in spectator mode. Record that fixture with:

```shell
GTR_RECORDING_PROFILE=showcase ./gradlew --no-daemon recordClientGameTest
```
