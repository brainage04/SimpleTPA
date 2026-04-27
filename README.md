# About
A server-side Fabric mod that adds "teleport ask" commands to Minecraft.

This TPA system adds TP requests, and the ability to accept or deny these requests.

Requests expire after 60 seconds if not accepted or denied.

Accepting a request will instantly teleport the requesting player to your world with your exact position and rotation.

Denying a request will instantly expire it.

Players can control which players they can automatically approve requests for using the `/tpautoaccept` commands.

Automatic request approval is controlled by the `simpletpa:allow_instant_tpa_accepting` gamerule, which is enabled by default.

# Commands
`/tprequest <name>` - Sends a TP request to the player with a given `name`.

`/tpaccept <name>` - Accepts a TP request from a given player.

`/tpaccept` - Accepts a TP request if one and ONLY one is pending.

`/tpdeny <name>` - Denies a TP request from a given player.

`/tpdeny` - Denies a TP request if one and ONLY one is pending.

`/tpautoaccept list` - Lists your instant accept whitelist.

`/tpautoaccept add <name>` - Allows a player to instantly teleport to you when they send a TP request and the gamerule allows it.

`/tpautoaccept remove <name>` - Removes a player from your instant accept whitelist.

`/tpautoaccept clear` - Clears your instant accept whitelist.
