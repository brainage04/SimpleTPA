#!/bin/sh
# captures-crops2 launcher: Fabric MC 26.2 client on isolated display :197 with its own
# PulseAudio null sink (round3_crops2). Argfile client-crops2.args is
# captures-fortnite/client-fortnite-4k.args with --gameDir repointed at captures-crops2/runtime
# and the mod set swapped for SimpleTPA + SimpleHomes + SpawnCommands + BrainageHUD.
export LD_LIBRARY_PATH="/nix/store/8yjiif0mmgzy5f95j53r46nf4nryjj3a-libglvnd-1.7.0/lib:/nix/store/6q9zxz6km0z4dmlxi6yrdp8rccbh49m1-mesa-26.1.8/lib:/nix/store/82jfzbq323cr46zzwfb4g4356163909d-glfw-minecraft-3.4/lib:/nix/store/hzkfj1z4i649j4lvijwzq53m92bq0xnc-libx11-1.8.13/lib:/nix/store/scdyyq28x2azh1c3mw390c9r9k179pp5-libxext-1.3.7/lib:/nix/store/hy6w2ww1imlysy07fn1w1gn2r659b88l-libxkbcommon-1.13.1/lib:/nix/store/icvh3bpwdwgh0wlmv7m9rswdqq59ydpm-libxrender-0.9.12/lib:/nix/store/k1ax8yipjfs4b7q6gxm3sa6vln5qglsa-libxinerama-1.1.6/lib:/nix/store/na2ajilb695n02pdfsg22lprbkywbdx7-libxrandr-1.5.5/lib:/nix/store/w4plh024q3lj86sgj4vn5s45wzprvzdx-libxi-1.8.3/lib:/nix/store/xaayvnn9askjmwy21b84bqmilkw9dmqb-libxxf86vm-1.1.7/lib:/nix/store/z0w0qkkig1lznz1ck29bfmhi974hsfm6-libxcursor-1.2.3/lib:/nix/store/36g6iazrffiy3bkrkq1cg2siqwvah164-libpulseaudio-17.0/lib:/nix/store/71ng8shgss15xpxmjs935cjdxsq2jwxd-alsa-lib-1.2.16.1/lib"
export LIBGL_DRIVERS_PATH="/nix/store/6q9zxz6km0z4dmlxi6yrdp8rccbh49m1-mesa-26.1.8/lib/dri"
export DISPLAY=:197
export ALSOFT_CONF="/home/thomas/01_TM/Coding/Websites/brainage04.github.io/.local-icon-variants/round3/captures-crops2/runtime/alsoft.conf"
export PULSE_SINK=round3_crops2
export LIBGL_ALWAYS_SOFTWARE=1
export LP_NUM_THREADS=8
exec /nix/store/b7hzgfqk2jfcbncp0vpabf73j0riqgq4-openjdk-25.0.4.1+1/bin/java "@/home/thomas/01_TM/Coding/Websites/brainage04.github.io/.local-icon-variants/round3/captures-crops2/client-crops2.args" "$@"
