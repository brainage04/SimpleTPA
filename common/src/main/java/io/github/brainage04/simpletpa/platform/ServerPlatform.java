package io.github.brainage04.simpletpa.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public interface ServerPlatform {
    String loaderName();

    void registerCommands(
            java.util.function.Consumer<CommandDispatcher<CommandSourceStack>> registration);

    void registerEndServerTick(java.util.function.Consumer<MinecraftServer> callback);

    /**
     * Runs after the server has read the world's saved data and before it loads the levels and
     * saves.
     */
    void registerServerStarting(java.util.function.Consumer<MinecraftServer> callback);

    void registerServerStarted(java.util.function.Consumer<MinecraftServer> callback);

    void registerServerStopping(java.util.function.Consumer<MinecraftServer> callback);

    void registerPlayerJoin(java.util.function.Consumer<ServerPlayer> callback);
}
