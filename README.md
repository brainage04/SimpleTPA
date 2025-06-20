# About
This is a Minecraft Fabric server-side mod that implements a very simple TPA system.

This TPA system allows for TP requests, and the ability to accept or deny these requests.

Requests expire after 60 seconds if not accepted or denied.

Accepting a request will instantly teleport the requesting player to your world with your exact position and rotation.

Denying a request will instantly expire it.

# Commands
`/tprequest <name>` - Sends a TP request to the player with a given `name`.

`/tpaccept <name>` - Accepts a TP request from a given player.

`/tpaccept` - Accepts a TP request if one and ONLY one is pending.

`/tpdeny <name>` - Denies a TP request from a given player.

`/tpdeny` - Denies a TP request if one and ONLY one is pending.