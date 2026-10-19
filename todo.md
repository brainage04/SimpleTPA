# To do

## Showcase from both players' perspectives (two real clients, side by side)

The showcase (`fabric/src/gametest/.../SimpleTPAClientGameTest.java`) records one real client while a Carpet fake player (`tpaFriend`) plays the other side. A future attempt could record two real Fabric clients and show both views side by side. Rough cost: about a day plus several test recordings; keeping the two clients in step is the main risk.

What it needs, mostly in FabricModdingConventions so other mods can use it too:

- **Connect by address:** today each client GameTest starts its own dedicated server inside its JVM on a random port (`ClientGameTestServers.withDedicatedServer`, online-mode off). Client A would host and write its port to a file; client B would connect to that address instead of starting a server, with a different username.
- **Two-client barrier:** both test scripts wait for each other at every step (for example through marker files in a shared directory), so a command typed on one client is answered on the other at the right moment.
- **Second recording task:** Gradle cannot run the same run task twice at once in one build, so the second client needs its own `recordClientGameTest` task and run directory (or a second copy of the checkout). The recorder already picks a free Xvfb display (:90 and up) and a uniquely named PulseAudio sink per run; two runs at once are untested.
- **Memory:** each client is its own JVM, roughly 3-4 GiB each (estimate, not measured), about 8 GiB together. That fits in thomas-pc's `heavy` cap (28 GiB). Both clients in one `heavy` job take one slot; two jobs take both slots.
- **Sync:** each recording's metadata has microsecond host-clock boundaries (`firstWorldTickStartUs`, `worldClearedUs`, frame times), so two recordings made on the same host can be aligned to within about a frame before composing.
- **Composition:** ffmpeg `hstack` after trimming both videos to a common start. Two 1920x1080 videos give 3840x1080, which is awkward to watch; scaling each half to 960x540 keeps 1920x1080 but makes chat and the caption panel small. Alternatively record each client at a smaller window or larger GUI scale.

Cheaper alternatives considered: two separate runs with the roles swapped, placed side by side by step markers (only a few ticks apart, not truly simultaneous); a spectator client filming the fake player (same cost as two clients, and the spectator sees its own chat rather than the fake player's).
